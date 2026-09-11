package cozy.document

import cozy.media.CozyVisualPage
import io.circe.{Json, JsonObject}
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import java.io.StringReader
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import scala.collection.mutable
import scala.util.control.NonFatal

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentLogicTree {
  final case class Claim(id: String)
  final case class Node(id: String, role: String)
  final case class Relation(id: String, relationType: String, from: String, to: String)
  final case class Structure(pattern: String, nodes: Vector[Node], relations: Vector[Relation])
  final case class Transition(id: String, relationType: String, fromStepId: String, toStepId: String)
  final case class Flow(id: String, transitions: Vector[Transition])
  final case class Step(
    id: String,
    semanticRole: String,
    claims: Vector[Claim],
    structure: Structure,
    flow: Flow,
    steps: Vector[Step]
  )
  final case class Core(id: String, root: Step)
  final case class StepBinding(id: String, title: String)
  final case class ClaimBinding(id: String, text: String)
  final case class NodeBinding(id: String, label: String)
  final case class Chrome(
    overviewLabel: String,
    overviewDocumentTitle: String,
    slidesDocumentTitle: String,
    pageCountLabel: String,
    claimsHeading: String,
    localStructureHeading: String,
    directChildrenHeading: String,
    directChildFlowHeading: String,
    noDirectChildren: String,
    noDirectChildFlowTransitions: String,
    previous: String,
    next: String,
    deck: String,
    navigationAriaLabel: String
  )
  final case class Format(
    id: String,
    coreId: String,
    coreIdentity: String,
    locale: String,
    stepBindings: Vector[StepBinding],
    claimBindings: Vector[ClaimBinding],
    nodeBindings: Vector[NodeBinding],
    chrome: Chrome
  )
  final case class Validated(
    core: Core,
    format: Format,
    coreIdentity: String,
    formatIdentity: String,
    depthFirstSteps: Vector[Step]
  ) {
    lazy val stepsById: Map[String, Step] = depthFirstSteps.map(value => value.id -> value).toMap
    lazy val titlesById: Map[String, String] = format.stepBindings.map(value => value.id -> value.title).toMap
    lazy val claimsById: Map[String, String] = format.claimBindings.map(value => value.id -> value.text).toMap
    lazy val labelsById: Map[String, String] = format.nodeBindings.map(value => value.id -> value.label).toMap
  }

  final case class LogicTreeFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private val _schema = "cozy.content-core.logic-tree.v1"
  private val _format_schema = "cozy.content-core.logic-tree-format.v1"
  private val _id_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _locale_pattern = "[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*".r
  private val _identity_pattern = "sha256:[0-9a-f]{64}".r

  def load(coreValue: Path, formatValue: Path): Validated = {
    val corepath = _admit_core(coreValue)
    val formatpath = _admit_format(formatValue)
    val corebytes = _read_bytes(corepath, "core")
    val formatbytes = _read_bytes(formatpath, "format")
    _validate(corebytes, _load_document(corepath, corebytes, "core"), formatbytes, _load_document(formatpath, formatbytes, "format"))
  }

  private[cozy] def validate(coreBytes: Array[Byte], coreValue: Json, formatValue: Json): Validated = {
    _decode_utf8(coreBytes, "$.core")
    _validate(coreBytes, coreValue, formatValue.noSpaces.getBytes(StandardCharsets.UTF_8), formatValue)
  }

  private def _validate(corebytes: Array[Byte], corevalue: Json, formatbytes: Array[Byte], formatvalue: Json): Validated = {
    _decode_utf8(corebytes, "$.core")
    _decode_utf8(formatbytes, "$.format")
    val core = _core(corevalue)
    _validate_tree(core.root)
    val format = _format(formatvalue)
    val coreidentity = "sha256:" + _sha256(corebytes)
    if (format.coreId != core.id)
      _fail("LOGIC_TREE_FORMAT_CORE", "$.coreId", "must exactly equal the direct Core id")
    if (format.coreIdentity != coreidentity)
      _fail("LOGIC_TREE_FORMAT_IDENTITY", "$.coreIdentity", "must bind the direct Core byte SHA-256 identity")
    _validate_wording(core, format)
    Validated(core, format, coreidentity, "sha256:" + _sha256(formatbytes), _depth_first(core.root))
  }

  private def _core(value: Json): Core = {
    val fields = _object(value, "$")
    _exact_fields(fields, Set("schema", "id", "root"), "$")
    if (_string(fields, "schema", "$") != _schema)
      _fail("LOGIC_TREE_SCHEMA", "$.schema", s"must be exactly ${_schema}")
    Core(_id(_string(fields, "id", "$"), "$.id"), _step(_field(fields, "root", "$"), "$.root"))
  }

  private def _step(value: Json, path: String): Step = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "semanticRole", "claims", "structure", "flow", "steps"), path)
    val claims = _array(_field(fields, "claims", path), s"$path.claims").zipWithIndex.map { case (item, index) =>
      Claim(_id(_string_value(item, s"$path.claims[$index]"), s"$path.claims[$index]"))
    }
    Step(
      _id(_string(fields, "id", path), s"$path.id"),
      _id(_string(fields, "semanticRole", path), s"$path.semanticRole"),
      claims,
      _structure(_field(fields, "structure", path), s"$path.structure"),
      _flow(_field(fields, "flow", path), s"$path.flow"),
      _array(_field(fields, "steps", path), s"$path.steps").zipWithIndex.map { case (item, index) => _step(item, s"$path.steps[$index]") }
    )
  }

  private def _structure(value: Json, path: String): Structure = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("pattern", "nodes", "relations"), path)
    Structure(
      _id(_string(fields, "pattern", path), s"$path.pattern"),
      _array(_field(fields, "nodes", path), s"$path.nodes").zipWithIndex.map { case (item, index) => _node(item, s"$path.nodes[$index]") },
      _array(_field(fields, "relations", path), s"$path.relations").zipWithIndex.map { case (item, index) => _relation(item, s"$path.relations[$index]") }
    )
  }

  private def _node(value: Json, path: String): Node = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "role"), path)
    Node(_id(_string(fields, "id", path), s"$path.id"), _id(_string(fields, "role", path), s"$path.role"))
  }

  private def _relation(value: Json, path: String): Relation = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "relationType", "from", "to"), path)
    Relation(
      _id(_string(fields, "id", path), s"$path.id"),
      _id(_string(fields, "relationType", path), s"$path.relationType"),
      _id(_string(fields, "from", path), s"$path.from"),
      _id(_string(fields, "to", path), s"$path.to")
    )
  }

  private def _flow(value: Json, path: String): Flow = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "transitions"), path)
    Flow(
      _id(_string(fields, "id", path), s"$path.id"),
      _array(_field(fields, "transitions", path), s"$path.transitions").zipWithIndex.map { case (item, index) => _transition(item, s"$path.transitions[$index]") }
    )
  }

  private def _transition(value: Json, path: String): Transition = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "relationType", "fromStepId", "toStepId"), path)
    Transition(
      _id(_string(fields, "id", path), s"$path.id"),
      _id(_string(fields, "relationType", path), s"$path.relationType"),
      _id(_string(fields, "fromStepId", path), s"$path.fromStepId"),
      _id(_string(fields, "toStepId", path), s"$path.toStepId")
    )
  }

  private def _format(value: Json): Format = {
    val fields = _object(value, "$")
    _exact_fields(fields, Set("schema", "id", "coreId", "coreIdentity", "locale", "stepBindings", "claimBindings", "nodeBindings", "chrome"), "$")
    if (_string(fields, "schema", "$") != _format_schema)
      _fail("LOGIC_TREE_FORMAT_SCHEMA", "$.schema", s"must be exactly ${_format_schema}")
    val identity = _string(fields, "coreIdentity", "$")
    if (!_identity_pattern.pattern.matcher(identity).matches())
      _fail("LOGIC_TREE_FORMAT_IDENTITY", "$.coreIdentity", "must be a sha256:<64 lowercase hexadecimal characters> identity")
    val locale = _string(fields, "locale", "$")
    if (!_locale_pattern.pattern.matcher(locale).matches())
      _fail("LOGIC_TREE_FORMAT_LOCALE", "$.locale", "must be a declared BCP-47 language tag")
    Format(
      _id(_string(fields, "id", "$"), "$.id"),
      _id(_string(fields, "coreId", "$"), "$.coreId"),
      identity,
      locale,
      _array(_field(fields, "stepBindings", "$"), "$.stepBindings").zipWithIndex.map { case (item, index) => _step_binding(item, s"$$.stepBindings[$index]") },
      _array(_field(fields, "claimBindings", "$"), "$.claimBindings").zipWithIndex.map { case (item, index) => _claim_binding(item, s"$$.claimBindings[$index]") },
      _array(_field(fields, "nodeBindings", "$"), "$.nodeBindings").zipWithIndex.map { case (item, index) => _node_binding(item, s"$$.nodeBindings[$index]") },
      _chrome(_field(fields, "chrome", "$"), "$.chrome")
    )
  }

  private def _step_binding(value: Json, path: String): StepBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "title"), path)
    StepBinding(_id(_string(fields, "id", path), s"$path.id"), _text(_string(fields, "title", path), s"$path.title"))
  }

  private def _claim_binding(value: Json, path: String): ClaimBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "text"), path)
    ClaimBinding(_id(_string(fields, "id", path), s"$path.id"), _text(_string(fields, "text", path), s"$path.text"))
  }

  private def _node_binding(value: Json, path: String): NodeBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "label"), path)
    NodeBinding(_id(_string(fields, "id", path), s"$path.id"), _text(_string(fields, "label", path), s"$path.label"))
  }

  private def _chrome(value: Json, path: String): Chrome = {
    val fields = _object(value, path)
    _exact_fields(fields, Set(
      "overviewLabel", "overviewDocumentTitle", "slidesDocumentTitle", "pageCountLabel",
      "claimsHeading", "localStructureHeading", "directChildrenHeading", "directChildFlowHeading",
      "noDirectChildren", "noDirectChildFlowTransitions", "previous", "next", "deck", "navigationAriaLabel"
    ), path)
    Chrome(
      _text(_string(fields, "overviewLabel", path), s"$path.overviewLabel"),
      _text(_string(fields, "overviewDocumentTitle", path), s"$path.overviewDocumentTitle"),
      _text(_string(fields, "slidesDocumentTitle", path), s"$path.slidesDocumentTitle"),
      _text(_string(fields, "pageCountLabel", path), s"$path.pageCountLabel"),
      _text(_string(fields, "claimsHeading", path), s"$path.claimsHeading"),
      _text(_string(fields, "localStructureHeading", path), s"$path.localStructureHeading"),
      _text(_string(fields, "directChildrenHeading", path), s"$path.directChildrenHeading"),
      _text(_string(fields, "directChildFlowHeading", path), s"$path.directChildFlowHeading"),
      _text(_string(fields, "noDirectChildren", path), s"$path.noDirectChildren"),
      _text(_string(fields, "noDirectChildFlowTransitions", path), s"$path.noDirectChildFlowTransitions"),
      _text(_string(fields, "previous", path), s"$path.previous"),
      _text(_string(fields, "next", path), s"$path.next"),
      _text(_string(fields, "deck", path), s"$path.deck"),
      _text(_string(fields, "navigationAriaLabel", path), s"$path.navigationAriaLabel")
    )
  }

  private def _validate_tree(root: Step): Unit = {
    val catalog = CozyVisualPage.fixedCatalog
    val stepids = mutable.Set.empty[String]
    val claimids = mutable.Set.empty[String]
    val nodeids = mutable.Set.empty[String]
    val relationids = mutable.Set.empty[String]
    val flowids = mutable.Set.empty[String]
    val transitionids = mutable.Set.empty[String]
    def _visit_(step: Step, path: String, ancestors: Set[String]): Unit = {
      if (ancestors.contains(step.id))
        _fail("LOGIC_TREE_CONTAINMENT_CYCLE", s"$path.id", s"Step ${step.id} re-enters an ancestor")
      if (!stepids.add(step.id))
        _fail("LOGIC_TREE_STEP_OWNERSHIP", s"$path.id", s"Step ${step.id} has more than one structural owner")
      step.claims.foreach { claim =>
        if (!claimids.add(claim.id)) _fail("LOGIC_TREE_IDENTITY", s"$path.claims", s"duplicate claim id: ${claim.id}")
      }
      if (!flowids.add(step.flow.id))
        _fail("LOGIC_TREE_IDENTITY", s"$path.flow.id", s"duplicate Flow id: ${step.flow.id}")
      _validate_structure(step.structure, catalog, path, nodeids, relationids)
      val childids = step.steps.map(_.id).toSet
      _validate_flow(step.flow, childids, catalog.relations.map(_.id).toSet, path, transitionids)
      step.steps.zipWithIndex.foreach { case (child, index) => _visit_(child, s"$path.steps[$index]", ancestors + step.id) }
    }
    _visit_(root, "$.root", Set.empty)
  }

  private def _validate_structure(
    structure: Structure,
    catalog: CozyVisualPage.Catalog,
    steppath: String,
    nodeids: mutable.Set[String],
    relationids: mutable.Set[String]
  ): Unit = {
    val path = s"$steppath.structure"
    val pattern = catalog.logicalPatterns.find(_.id == structure.pattern).getOrElse(
      _fail("LOGIC_TREE_PATTERN", s"$path.pattern", s"must use a CozyVisualPage logical pattern: ${structure.pattern}")
    )
    _unique(structure.nodes.map(_.id), s"$path.nodes", "local node id")
    _unique(structure.relations.map(_.id), s"$path.relations", "local relation id")
    structure.nodes.foreach { node =>
      if (!nodeids.add(node.id)) _fail("LOGIC_TREE_IDENTITY", s"$path.nodes", s"duplicate node id: ${node.id}")
    }
    structure.relations.foreach { relation =>
      if (!relationids.add(relation.id)) _fail("LOGIC_TREE_IDENTITY", s"$path.relations", s"duplicate relation id: ${relation.id}")
    }
    val nodes = structure.nodes.map(value => value.id -> value).toMap
    val admittedroles = pattern.nodeRoles.map(_.role).toSet
    structure.nodes.foreach { node =>
      if (!admittedroles.contains(node.role))
        _fail("LOGIC_TREE_NODE_ROLE", s"$path.nodes.${node.id}.role", s"role is not admitted by ${pattern.id}: ${node.role}")
    }
    pattern.nodeRoles.foreach { rule =>
      val count = structure.nodes.count(_.role == rule.role)
      if (count < rule.min || count > rule.max)
        _fail("LOGIC_TREE_NODE_CARDINALITY", s"$path.nodes", s"role ${rule.role} requires ${rule.min}..${rule.max} nodes, found $count")
    }
    val relationrules = pattern.relationRules.map(value => value.relation -> value).toMap
    structure.relations.foreach { relation =>
      if (!nodes.contains(relation.from) || !nodes.contains(relation.to))
        _fail("LOGIC_TREE_RELATION_ENDPOINT", s"$path.relations.${relation.id}", "relation endpoints must resolve inside this exact local Structure")
      val rule = relationrules.getOrElse(relation.relationType,
        _fail("LOGIC_TREE_RELATION_TYPE", s"$path.relations.${relation.id}.relationType", s"relation is not admitted by ${pattern.id}: ${relation.relationType}")
      )
      if (!rule.fromRoles.contains(nodes(relation.from).role) || !rule.toRoles.contains(nodes(relation.to).role))
        _fail("LOGIC_TREE_RELATION_DIRECTION", s"$path.relations.${relation.id}", s"relation endpoints do not satisfy ${relation.relationType} role direction")
    }
    pattern.relationRules.foreach { rule =>
      val relations = structure.relations.filter(_.relationType == rule.relation)
      if (relations.size < rule.min || relations.size > rule.max)
        _fail("LOGIC_TREE_RELATION_CARDINALITY", s"$path.relations", s"relation ${rule.relation} requires ${rule.min}..${rule.max} values, found ${relations.size}")
      _unique(relations.map(value => s"${value.from}\u0000${value.to}"), s"$path.relations", s"${rule.relation} endpoint")
    }
    pattern.relationRules.map(_.topology).distinct.foreach {
      case "linear" => _validate_linear(structure, path)
      case "acyclic" => _validate_acyclic(structure, path)
      case "bipartite" => ()
      case value => _fail("LOGIC_TREE_TOPOLOGY", path, s"unsupported CozyVisualPage topology: $value")
    }
  }

  private def _validate_flow(
    flow: Flow,
    childids: Set[String],
    relationtypes: Set[String],
    steppath: String,
    transitionids: mutable.Set[String]
  ): Unit = {
    val path = s"$steppath.flow"
    _unique(flow.transitions.map(_.id), s"$path.transitions", "local transition id")
    _unique(flow.transitions.map(value => s"${value.relationType}\u0000${value.fromStepId}\u0000${value.toStepId}"), s"$path.transitions", "local transition endpoint")
    flow.transitions.foreach { transition =>
      if (!transitionids.add(transition.id)) _fail("LOGIC_TREE_IDENTITY", s"$path.transitions", s"duplicate transition id: ${transition.id}")
      if (!relationtypes.contains(transition.relationType))
        _fail("LOGIC_TREE_FLOW_TYPE", s"$path.transitions.${transition.id}.relationType", "must use a CozyVisualPage relation type")
      if (!childids.contains(transition.fromStepId) || !childids.contains(transition.toStepId))
        _fail("LOGIC_TREE_FLOW_SCOPE", s"$path.transitions.${transition.id}", "Flow endpoints must resolve only direct child Step ids")
      if (transition.fromStepId == transition.toStepId)
        _fail("LOGIC_TREE_FLOW_SELF", s"$path.transitions.${transition.id}", "Flow self-links are not admitted")
    }
  }

  private def _validate_linear(structure: Structure, path: String): Unit = {
    val incoming = structure.relations.groupBy(_.to).map { case (id, values) => id -> values.size }.withDefaultValue(0)
    val outgoing = structure.relations.groupBy(_.from).map { case (id, values) => id -> values.size }.withDefaultValue(0)
    if (structure.relations.size != structure.nodes.size - 1 || structure.nodes.exists(node => incoming(node.id) > 1 || outgoing(node.id) > 1))
      _fail("LOGIC_TREE_LINEAR", path, "linear Structure must be one directed chain")
    val starts = structure.nodes.filter(node => incoming(node.id) == 0)
    val ends = structure.nodes.filter(node => outgoing(node.id) == 0)
    if (starts.size != 1 || ends.size != 1)
      _fail("LOGIC_TREE_LINEAR", path, "linear Structure must have exactly one start and one end")
    val next = structure.relations.map(value => value.from -> value.to).toMap
    var seen = Set.empty[String]
    var current = starts.head.id
    while (current.nonEmpty && !seen.contains(current)) {
      seen += current
      current = next.getOrElse(current, "")
    }
    if (seen.size != structure.nodes.size || current.nonEmpty)
      _fail("LOGIC_TREE_LINEAR", path, "linear Structure must be connected and acyclic")
  }

  private def _validate_acyclic(structure: Structure, path: String): Unit = {
    val adjacency = structure.relations.groupBy(_.from).map { case (id, values) => id -> values.map(_.to) }.withDefaultValue(Vector.empty)
    var visiting = Set.empty[String]
    var visited = Set.empty[String]
    def _visit_(id: String): Unit = {
      if (visiting.contains(id)) _fail("LOGIC_TREE_ACYCLIC", s"$path.relations", "Structure relation graph contains a cycle")
      if (!visited.contains(id)) {
        visiting += id
        adjacency(id).foreach(_visit_)
        visiting -= id
        visited += id
      }
    }
    structure.nodes.foreach(node => _visit_(node.id))
  }

  private def _validate_wording(core: Core, format: Format): Unit = {
    val steps = _depth_first(core.root)
    val claims = steps.flatMap(_.claims)
    val nodes = steps.flatMap(_.structure.nodes)
    _exact_bindings(steps.map(_.id), format.stepBindings.map(_.id), "$.stepBindings", "Step")
    _exact_bindings(claims.map(_.id), format.claimBindings.map(_.id), "$.claimBindings", "claim")
    _exact_bindings(nodes.map(_.id), format.nodeBindings.map(_.id), "$.nodeBindings", "node")
  }

  private def _exact_bindings(coreids: Vector[String], bindingids: Vector[String], path: String, label: String): Unit = {
    _unique(bindingids, path, s"$label binding id")
    if (coreids.toSet != bindingids.toSet || coreids.size != bindingids.size)
      _fail("LOGIC_TREE_FORMAT_COVERAGE", path, s"must provide one and only one wording binding for every Core $label")
  }

  private def _depth_first(root: Step): Vector[Step] = root +: root.steps.flatMap(_depth_first)

  private def _admit_core(value: Path): Path = {
    val path = _admit_file(value, "core")
    if (path.getFileName.toString != "core.yaml")
      _fail("LOGIC_TREE_PATH", "$.core", "semantic authority must be a direct regular file named exactly core.yaml")
    path
  }

  private def _admit_format(value: Path): Path = {
    val path = _admit_file(value, "format")
    if (!path.getFileName.toString.endsWith(".yaml"))
      _fail("LOGIC_TREE_PATH", "$.format", "Format authority must be a direct .yaml file")
    path
  }

  private def _admit_file(value: Path, label: String): Path = {
    val path = try value.toAbsolutePath.normalize() catch {
      case NonFatal(_) => _fail("LOGIC_TREE_PATH", s"$$.$label", "path is invalid")
    }
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _fail("LOGIC_TREE_PATH", s"$$.$label", "must be a direct regular non-symlink file")
    val parent = Option(path.getParent).getOrElse(_fail("LOGIC_TREE_PATH", s"$$.$label", "has no safe parent"))
    if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
      _fail("LOGIC_TREE_PATH", s"$$.$label", "parent must be a direct non-symlink directory")
    path
  }

  private def _read_bytes(path: Path, label: String): Array[Byte] =
    try Files.readAllBytes(path) catch {
      case NonFatal(_) => _fail("LOGIC_TREE_SOURCE", s"$$.$label", "cannot be read")
    }

  private def _load_document(path: Path, bytes: Array[Byte], label: String): Json =
    try {
      val text = _decode_utf8(bytes, s"$$.$label")
      val options = new LoaderOptions()
      options.setAllowDuplicateKeys(false)
      new Yaml(options).load(new StringReader(text))
      StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
    } catch {
      case fault: LogicTreeFault => throw fault
      case NonFatal(_) => _fail("LOGIC_TREE_SOURCE", s"$$.$label", "must be a well-formed JSON/YAML document without lossy structure")
    }

  private def _decode_utf8(bytes: Array[Byte], path: String): String =
    try StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString catch {
      case NonFatal(_) => _fail("LOGIC_TREE_SOURCE", path, "must be valid UTF-8")
    }

  private def _object(value: Json, path: String): JsonObject = value.asObject.getOrElse(_fail("LOGIC_TREE_STRUCTURE", path, "must be an object"))
  private def _array(value: Json, path: String): Vector[Json] = value.asArray.map(_.toVector).getOrElse(_fail("LOGIC_TREE_STRUCTURE", path, "must be an array"))
  private def _field(fields: JsonObject, name: String, path: String): Json = fields(name).getOrElse(_fail("LOGIC_TREE_STRUCTURE", path, s"missing required field: $name"))
  private def _string(fields: JsonObject, name: String, path: String): String = _string_value(_field(fields, name, path), s"$path.$name")
  private def _string_value(value: Json, path: String): String = value.asString.getOrElse(_fail("LOGIC_TREE_STRUCTURE", path, "must be a string"))

  private def _exact_fields(fields: JsonObject, expected: Set[String], path: String): Unit = {
    if (fields.keys.toSet != expected)
      _fail("LOGIC_TREE_FIELDS", path, s"must contain exactly: ${expected.toVector.sorted.mkString(", ")}")
  }

  private def _id(value: String, path: String): String = {
    if (!_id_pattern.pattern.matcher(value).matches()) _fail("LOGIC_TREE_ID", path, "must be a nonempty stable identifier")
    value
  }

  private def _text(value: String, path: String): String = {
    if (value.isEmpty || value != value.trim) _fail("LOGIC_TREE_WORDING", path, "must be nonempty and trimmed")
    value
  }

  private def _unique(values: Vector[String], path: String, label: String): Unit = {
    values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach { value =>
      _fail("LOGIC_TREE_IDENTITY", path, s"duplicate $label: $value")
    }
  }

  private def _sha256(bytes: Array[Byte]): String = {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    digest.map(value => f"${value & 0xff}%02x").mkString
  }

  private def _fail(code: String, path: String, reason: String): Nothing = throw LogicTreeFault(code, path, reason)
}
