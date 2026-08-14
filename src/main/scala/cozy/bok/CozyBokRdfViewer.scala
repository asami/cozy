package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.bok.scenario.ScenarioMetadata
import cozy.bok.BibliographyEntry._
import cozy.config.CozyProjectYamlConfig
import cozy.publication.{CozyArticleMediaBuildContext, CozyArticleMediaInfographicCommand, CozyArticleMediaInfographicEvidence, CozyArticleMediaVideoCommand}
import cozy.video.{CozyVideo, CozyVideoPublisher}
import org.smartdox.{Body, Document, Dox}
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.Dox2HtmlTransformer
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.metadata.DocumentMetaData
import org.goldenport.i18n.I18NContext
import java.net.URLEncoder
import java.time.{Instant, LocalDate, LocalDateTime, YearMonth, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.control.NonFatal
import scala.sys.process._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import io.circe.syntax._

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokRdfViewer {
  self: CozyBokImplementation.type =>
  private[bok] def _rdf_viewer_script(locale: String): String =
    s"""(function() {
       |  const root = document.querySelector('.bok-rdf-workspace');
       |  const graphTarget = document.getElementById('bok-rdf-viewer-graph');
       |  const graphStatus = document.getElementById('bok-rdf-viewer-status');
       |  const triplesTarget = document.getElementById('bok-rdf-triples-view');
       |  const triplesStatus = document.getElementById('bok-rdf-triples-status');
       |  const nodeListTarget = document.getElementById('bok-rdf-node-list-view');
       |  const input = document.getElementById('bok-rdf-category-filter');
       |  const termInput = document.getElementById('bok-rdf-term-filter');
       |  const tagInput = document.getElementById('bok-rdf-tag-filter');
       |  if (!root || !graphTarget || !graphStatus) return;
       |  const params = new URLSearchParams(window.location.search);
       |  const initialCategory = params.get('category') || '';
       |  const initialTerm = params.get('term') || '';
       |  const initialTag = params.get('tag') || '';
       |  const initialNode = params.get('node') || '';
       |  let focusedNodeId = initialNode;
       |  if (input) input.value = initialCategory;
       |  if (termInput) termInput.value = initialTerm;
       |  if (tagInput) tagInput.value = initialTag;
       |  function activate(view) {
       |    document.querySelectorAll('[data-rdf-view]').forEach(function(button) {
       |      button.classList.toggle('is-active', button.getAttribute('data-rdf-view') === view);
       |      button.setAttribute('aria-selected', button.getAttribute('data-rdf-view') === view ? 'true' : 'false');
       |    });
       |    document.querySelectorAll('[data-rdf-panel]').forEach(function(panel) {
       |      panel.classList.toggle('is-active', panel.getAttribute('data-rdf-panel') === view);
       |    });
       |  }
       |  document.querySelectorAll('[data-rdf-view]').forEach(function(button) {
       |    button.addEventListener('click', function() { activate(button.getAttribute('data-rdf-view')); });
       |  });
       |  function hasTerm(item, term) {
       |    const sieTerms = item && item.sie ? (item.sie.termRefs || item.sie.term_refs || []) : [];
       |    return !term || (item.terms || []).indexOf(term) >= 0 || sieTerms.indexOf(term) >= 0;
       |  }
       |  function hasTag(item, tag) {
       |    const sieTags = item && item.sie ? (item.sie.tags || []) : [];
       |    return !tag || (item.tags || []).indexOf(tag) >= 0 || sieTags.indexOf(tag) >= 0;
       |  }
       |  function termLabel(termIndex, term) {
       |    const item = termIndex[term];
       |    return item ? (item.title || term) : term;
       |  }
       |  function activeTermIndex() {
       |    return window.__bokRdfTermIndex || {};
       |  }
       |  function nodeTerms(node) {
       |    const index = activeTermIndex();
       |    return asArray((node && node.terms) || []).map(function(term) { return index[term]; }).filter(Boolean);
       |  }
       |  function termAnalysisKind(term) {
       |    if (term && term.term_type === 'event') return '${_javascript_string(_ui(locale, "term.analysis.koto"))}';
       |    if (term && term.term_type === 'rule') return '${_javascript_string(_ui(locale, "term.analysis.rule"))}';
       |    return '${_javascript_string(_ui(locale, "term.analysis.mono"))}';
       |  }
       |  function termGeneralCmlLinks(term) {
       |    const keys = ['entity', 'value', 'powertype', 'statemachine', 'rule', 'event', 'operation', 'component', 'service'];
       |    const result = [];
       |    asArray(term && term.cml).forEach(function(item) {
       |      if (!item || typeof item !== 'object') return;
       |      if (item.kind && (item.name || item.element_ref || item.value)) result.push(item.kind + ': ' + (item.name || item.element_ref || item.value));
       |      keys.forEach(function(key) { if (item[key]) result.push(key + ': ' + item[key]); });
       |    });
       |    return result;
       |  }
       |  function termCmlLinks(term) {
       |    const event = term && term.event ? term.event : {};
       |    return termGeneralCmlLinks(term).concat([
       |      ['component', event.cml_component],
       |      ['event', event.cml_event],
       |      ['statemachine', event.cml_statemachine]
       |    ].filter(function(item) { return item[1]; }).map(function(item) { return item[0] + ': ' + item[1]; }));
       |  }
       |  function nodeMonoKotoValues(node) {
       |    return uniqueStrings(nodeTerms(node).map(function(term) { return termAnalysisKind(term); }));
       |  }
       |  function nodeCmlLinkValues(node) {
       |    return uniqueStrings(nodeTerms(node).map(termCmlLinks));
       |  }
       |  function renderGraph(data, category, term, tag, termIndex) {
    const allNodes = (data.nodes || []).slice().sort(function(a, b) {
      const degree = (b.degree || 0) - (a.degree || 0);
      return degree !== 0 ? degree : String(a.id || '').localeCompare(String(b.id || ''));
    });
    const allEdges = (data.edges || []).slice().sort(function(a, b) {
      return String(a.source || '').localeCompare(String(b.source || '')) ||
        String(a.target || '').localeCompare(String(b.target || '')) ||
        String(a.predicate || a.label || '').localeCompare(String(b.predicate || b.label || ''));
    });
    const categoryNodeIds = new Set();
    const termNodeIds = new Set();
    const tagNodeIds = new Set();
    allNodes.forEach(function(node) {
      if (category && node.category === category) categoryNodeIds.add(node.id);
      if (term && hasTerm(node, term)) termNodeIds.add(node.id);
      if (tag && hasTag(node, tag)) tagNodeIds.add(node.id);
    });
    const matchingEdges = allEdges.filter(function(edge) {
      const matchesCategory = !category || edge.category === category || categoryNodeIds.has(edge.source) || categoryNodeIds.has(edge.target);
      const matchesTerm = !term || hasTerm(edge, term) || termNodeIds.has(edge.source) || termNodeIds.has(edge.target);
      const matchesTag = !tag || hasTag(edge, tag) || tagNodeIds.has(edge.source) || tagNodeIds.has(edge.target);
      return matchesCategory && matchesTerm && matchesTag;
    });
    const edgeNodeIds = new Set();
    matchingEdges.forEach(function(edge) {
      if (edge.source) edgeNodeIds.add(edge.source);
      if (edge.target) edgeNodeIds.add(edge.target);
    });
    const matchingNodes = allNodes.filter(function(node) {
      const matchesCategory = !category || node.category === category || edgeNodeIds.has(node.id);
      const matchesTerm = !term || hasTerm(node, term) || edgeNodeIds.has(node.id);
      const matchesTag = !tag || hasTag(node, tag) || edgeNodeIds.has(node.id);
      return matchesCategory && matchesTerm && matchesTag;
    }).slice(0, 120);
    const nodeIds = new Set(matchingNodes.map(function(node) { return node.id; }));
    const edges = matchingEdges.filter(function(edge) {
      return nodeIds.has(edge.source) && nodeIds.has(edge.target);
    }).slice(0, 180);
    const focus = focusedNodeId && nodeIds.has(focusedNodeId) ? focusedNodeId : null;
    if (focusedNodeId && !focus) focusedNodeId = null;
    const focusGraph = focus ? focusedGraphSlice(focus, matchingNodes, edges) : {
      nodes: matchingNodes,
      edges: edges,
      roles: {}
    };
    const visibleNodes = focusGraph.nodes;
    const visibleEdges = focusGraph.edges;
    graphStatus.textContent = visibleNodes.length + ' nodes / ' + visibleEdges.length + ' edges' + (focus ? ' / focus: ' + compactRdfLabel(focus) : '') + (data.truncated ? ' (truncated)' : '');
    graphTarget.innerHTML =
      '<div class="bok-rdf-graph-summary">' +
        '<span><b>' + visibleNodes.length + '</b>nodes</span>' +
        '<span><b>' + visibleEdges.length + '</b>edges</span>' +
        '<span><b>' + escapeHtml(category || 'all') + '</b>category</span>' +
        '<span><b>' + escapeHtml(term ? termLabel(termIndex, term) : 'all') + '</b>term</span>' +
        '<span><b>' + escapeHtml(tag || 'all') + '</b>tag</span>' +
      '</div>' +
      '<div class="bok-rdf-focus-bar">' +
        (focus ? '<span>${_javascript_string(_ui(locale, "rdf.graph.focus.node"))}: <b>' + escapeHtml(compactRdfLabel(focus)) + '</b></span><button type="button" data-rdf-clear-focus="true">${_javascript_string(_ui(locale, "rdf.graph.focus.clear"))}</button>' : '<span>${_javascript_string(_ui(locale, "rdf.graph.focus.help"))}</span>') +
      '</div>' +
      '<div class="bok-rdf-graph-layout">' +
        '<div class="bok-rdf-graph-canvas" data-rdf-graph-canvas="true"></div>' +
      '</div>';
    const canvas = graphTarget.querySelector('[data-rdf-graph-canvas]');
    if (!canvas) return;
    if (visibleNodes.length === 0) {
      canvas.innerHTML = '<div class="bok-rdf-empty">No graph nodes match the current filter.</div>';
      return;
    }
    const clearButton = graphTarget.querySelector('[data-rdf-clear-focus]');
    if (clearButton) clearButton.addEventListener('click', function() { focusedNodeId = null; renderGraph(data, category, term, tag, termIndex); });
    graphTarget.querySelectorAll('[data-rdf-focus-node]').forEach(function(button) {
      button.addEventListener('click', function() {
        const node = visibleNodes.filter(function(item) { return item.id === button.getAttribute('data-rdf-focus-node'); })[0];
        if (node) showNodeDetail(node, visibleEdges);
      });
    });
    renderGraphSvg(canvas, visibleNodes, visibleEdges, focusGraph.roles, focus);
    renderNodeListView(visibleNodes, visibleEdges, focusGraph.roles, focus);
  }
  function renderNodeListView(nodes, edges, roles, focus) {
    if (!nodeListTarget) return;
    const sortedNodes = nodes.slice().sort(function(a, b) {
      const roleOrder = {focus: 0, near: 1, schema: 2, normal: 3};
      const ar = roleOrder[roles[a.id] || 'normal'] == null ? 9 : roleOrder[roles[a.id] || 'normal'];
      const br = roleOrder[roles[b.id] || 'normal'] == null ? 9 : roleOrder[roles[b.id] || 'normal'];
      return ar - br || String(a.category || '').localeCompare(String(b.category || '')) || String(compactNodeLabel(a)).localeCompare(String(compactNodeLabel(b)));
    });
    const edgeCounts = {};
    edges.forEach(function(edge) {
      if (edge.source) edgeCounts[edge.source] = (edgeCounts[edge.source] || 0) + 1;
      if (edge.target) edgeCounts[edge.target] = (edgeCounts[edge.target] || 0) + 1;
    });
    nodeListTarget.innerHTML =
      '<div class="bok-rdf-node-list-header">' +
        '<div><span class="bok-dashboard-eyebrow">RDF nodes</span><h2>${_javascript_string(_ui(locale, "rdf.graph.view.nodes"))}</h2><p>${_javascript_string(_ui(locale, "rdf.graph.nodes.description"))}</p></div>' +
        '<div class="bok-rdf-node-list-summary"><span><b>' + sortedNodes.length + '</b>nodes</span><span><b>' + edges.length + '</b>edges</span></div>' +
      '</div>' +
      '<div class="bok-rdf-node-list-grid">' +
        (sortedNodes.length ? sortedNodes.map(function(node) { return nodeListCard(node, edgeCounts[node.id] || 0, roles[node.id] || 'normal', focus); }).join('') : '<div class="bok-rdf-empty">No RDF nodes match the current filter.</div>') +
      '</div>';
  }
  function nodeListCard(node, connections, role, focus) {
    const interpretation = schemaInterpretation(node);
    const roleLabel = role === 'focus' ? 'focus' : (role === 'near' ? 'near' : (role === 'schema' ? 'schema' : 'visible'));
    const category = node.category || '-';
    const type = node.node_type || node.type || '-';
    const monokoto = nodeMonoKotoValues(node);
    const cmllinks = nodeCmlLinkValues(node);
    return '<article class="bok-rdf-node-list-card bok-rdf-node-list-card-' + escapeHtml(cssName(roleLabel)) + '">' +
      '<div class="bok-rdf-node-list-card-head"><span>' + escapeHtml(roleLabel) + '</span><a href="node.html?id=' + encodeURIComponent(node.id || '') + '">${_javascript_string(_ui(locale, "rdf.graph.node.full.detail"))}</a></div>' +
      '<h3 title="' + escapeHtml(node.id || '') + '">' + escapeHtml(compactNodeLabel(node)) + '</h3>' +
      '<dl>' +
        '<dt>Category</dt><dd>' + escapeHtml(category) + '</dd>' +
        '<dt>Type</dt><dd>' + escapeHtml(type) + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "term.analysis.kind"))}</dt><dd>' + escapeHtml(monokoto.length ? monokoto.join(', ') : '-') + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "term.analysis.cml.linkage"))}</dt><dd>' + escapeHtml(cmllinks.length ? cmllinks.join(', ') : '-') + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "rdf.graph.node.connections"))}</dt><dd>' + escapeHtml(node.degree == null ? connections : node.degree) + '</dd>' +
        '<dt>Schema</dt><dd><code>' + escapeHtml(interpretation.informationSchema) + '</code></dd>' +
        '<dt>Expansion</dt><dd>' + escapeHtml(interpretation.expansion) + '</dd>' +
      '</dl>' +
      '<div class="bok-rdf-node-list-card-actions"><a href="index.html?node=' + encodeURIComponent(node.id || '') + '">${_javascript_string(_ui(locale, "rdf.graph.node.neighborhood"))}</a></div>' +
    '</article>';
  }
  function focusedGraphSlice(focus, nodes, edges) {
    const nodeMap = {};
    nodes.forEach(function(node) { nodeMap[node.id] = node; });
    const near = new Set([focus]);
    const oneHopEdges = [];
    edges.forEach(function(edge) {
      if (edge.source === focus && edge.target) {
        near.add(edge.target);
        oneHopEdges.push(edge);
      }
      if (edge.target === focus && edge.source) {
        near.add(edge.source);
        oneHopEdges.push(edge);
      }
    });
    const visibleIds = new Set(Array.from(near));
    const schemaEdges = [];
    const schemaEdgeKeys = new Set();
    for (let depth = 0; depth < 4; depth += 1) {
      let changed = false;
      edges.forEach(function(edge) {
        const sourceVisible = visibleIds.has(edge.source);
        const targetVisible = visibleIds.has(edge.target);
        if (!sourceVisible && !targetVisible) return;
        const sourceNode = nodeMap[edge.source];
        const targetNode = nodeMap[edge.target];
        const requiredFromSource = sourceVisible && isSchemaRequiredEdge(edge, sourceNode, 'outgoing');
        const requiredFromTarget = targetVisible && isSchemaRequiredEdge(edge, targetNode, 'incoming');
        if (!requiredFromSource && !requiredFromTarget) return;
        const key = edgeKey(edge);
        if (!schemaEdgeKeys.has(key)) {
          schemaEdgeKeys.add(key);
          schemaEdges.push(edge);
        }
        if (edge.source && !visibleIds.has(edge.source)) {
          visibleIds.add(edge.source);
          changed = true;
        }
        if (edge.target && !visibleIds.has(edge.target)) {
          visibleIds.add(edge.target);
          changed = true;
        }
      });
      if (!changed) break;
    }
    const roles = {};
    Array.from(visibleIds).forEach(function(id) { roles[id] = near.has(id) ? 'near' : 'schema'; });
    roles[focus] = 'focus';
    const visibleNodes = nodes.filter(function(node) { return visibleIds.has(node.id); }).slice(0, 100);
    const visibleNodeIds = new Set(visibleNodes.map(function(node) { return node.id; }));
    const visibleEdgeKeys = new Set();
    const visibleEdges = [];
    oneHopEdges.concat(schemaEdges).forEach(function(edge) {
      const key = edgeKey(edge);
      if (!visibleEdgeKeys.has(key) && visibleNodeIds.has(edge.source) && visibleNodeIds.has(edge.target)) {
        visibleEdgeKeys.add(key);
        visibleEdges.push(edge);
      }
    });
    return {nodes: visibleNodes, edges: visibleEdges.slice(0, 160), roles: roles};
  }
  function isSchemaRequiredEdge(edge, node, direction) {
    if (!node) return isDefaultDescriptionPredicate(edge.predicate || edge.label);
    const schemaPredicates = schemaRequiredPredicates(node, direction);
    if (schemaPredicates.length === 0) return isDefaultDescriptionPredicate(edge.predicate || edge.label);
    return schemaPredicates.some(function(predicate) { return predicateMatches(edge, predicate); });
  }
  function selectedInformationSchema(node) {
    const informationView = currentInformationView();
    const candidates = asArray(informationView.informationSchemas);
    const safeNode = node || {};
    const nodeSchema = safeNode.schema || {};
    const requested = String(nodeSchema.informationSchema || nodeSchema.information_schema || safeNode.informationSchema || safeNode.information_schema || '').trim();
    if (requested) {
      const direct = candidates.filter(function(item) {
        return String(item.name || item.id || '') === requested || String(item.label || '') === requested;
      })[0];
      if (direct) return direct;
    }
    const nodeType = String(safeNode.informationType || safeNode.information_type || safeNode.node_type || safeNode.type || '').trim();
    const category = String(safeNode.category || '').trim();
    const matched = candidates.filter(function(item) {
      const match = item.match || {};
      const nodeTypes = uniqueStrings([item.nodeTypes, item.node_types, match.nodeTypes, match.node_types]);
      const categories = uniqueStrings([item.categories, match.categories]);
      const typeOk = nodeTypes.length === 0 || nodeTypes.indexOf(nodeType) >= 0;
      const categoryOk = categories.length === 0 || categories.indexOf(category) >= 0;
      return typeOk && categoryOk;
    })[0];
    if (matched) return matched;
    return candidates[0] || {};
  }
  function informationSchemaPredicates(informationSchema, kind) {
    if (!informationSchema) return [];
    const schema = informationSchema.schema || {};
    const fields = {
      required: [informationSchema.requiredPredicates, informationSchema.required_predicates, schema.requiredPredicates, schema.required_predicates],
      descriptive: [informationSchema.descriptivePredicates, informationSchema.descriptive_predicates, schema.descriptivePredicates, schema.descriptive_predicates],
      outgoing: [informationSchema.outgoingRequiredPredicates, informationSchema.outgoing_required_predicates, schema.outgoingRequiredPredicates, schema.outgoing_required_predicates],
      incoming: [informationSchema.incomingRequiredPredicates, informationSchema.incoming_required_predicates, schema.incomingRequiredPredicates, schema.incoming_required_predicates]
    };
    return uniqueStrings(fields[kind] || []);
  }
  function schemaRequiredPredicates(node, direction) {
    const schema = node.schema || {};
    const informationSchema = selectedInformationSchema(node);
    let values = [];
    [
      informationSchemaPredicates(informationSchema, 'required'),
      informationSchemaPredicates(informationSchema, 'descriptive'),
      informationSchemaPredicates(informationSchema, direction),
      node.requiredPredicates,
      node.required_predicates,
      node.descriptivePredicates,
      node.descriptive_predicates,
      schema.requiredPredicates,
      schema.required_predicates,
      schema.descriptivePredicates,
      schema.descriptive_predicates,
      schema[direction + 'RequiredPredicates'],
      schema[direction + '_required_predicates']
    ].forEach(function(item) { values = values.concat(asArray(item)); });
    return values.map(function(value) { return String(value); }).filter(Boolean);
  }
  function schemaInterpretation(node) {
    const schema = node.schema || {};
    const informationView = currentInformationView();
    const profile = informationView.predicateProfile || currentPredicateProfile();
    const informationSchema = selectedInformationSchema(node);
    const required = uniqueStrings([informationSchemaPredicates(informationSchema, 'required'), node.requiredPredicates, node.required_predicates, schema.requiredPredicates, schema.required_predicates]);
    const descriptive = uniqueStrings([informationSchemaPredicates(informationSchema, 'descriptive'), node.descriptivePredicates, node.descriptive_predicates, schema.descriptivePredicates, schema.descriptive_predicates]);
    const outgoing = uniqueStrings([informationSchemaPredicates(informationSchema, 'outgoing'), schema.outgoingRequiredPredicates, schema.outgoing_required_predicates]);
    const incoming = uniqueStrings([informationSchemaPredicates(informationSchema, 'incoming'), schema.incomingRequiredPredicates, schema.incoming_required_predicates]);
    const fallback = required.length + descriptive.length + outgoing.length + incoming.length === 0;
    const schemaPredicates = uniqueStrings([required, descriptive, outgoing, incoming]);
    const identityPredicates = profileRolePredicates(profile, 'identity');
    const descriptivePredicates = uniqueStrings([profileRolePredicates(profile, 'descriptive'), descriptive]);
    const linkPredicates = profileRolePredicates(profile, 'link');
    const hierarchyPredicates = profileRolePredicates(profile, 'hierarchy');
    const provenancePredicates = profileRolePredicates(profile, 'provenance');
    return {
      informationViewName: informationView.name || 'cncf-rdf-1.5-hop-information-view-v1',
      informationViewConcept: informationView.concept || '1.5+hop',
      informationSchema: informationSchema.name || informationSchema.id || 'rdf-resource-information-v1',
      informationSchemaLabel: informationSchema.label || informationSchema.name || informationSchema.id || 'RDF Resource Information',
      profile: profile.name || 'cncf-rdf-1.5-hop-v1',
      informationViewLabel: informationView.label || 'CNCF RDF 1.5+hop Information View',
      informationType: node.informationType || node.information_type || node.node_type || node.type || 'unknown',
      category: node.category || '-',
      predicateRoles: fallback ? Object.keys(profile.roles || {}).sort() : predicateRoles(schemaPredicates, profile),
      identityPredicates: identityPredicates,
      descriptivePredicates: descriptivePredicates,
      linkPredicates: linkPredicates,
      hierarchyPredicates: hierarchyPredicates,
      provenancePredicates: provenancePredicates,
      requiredPredicates: required,
      nodeDescriptivePredicates: descriptive,
      outgoingRequiredPredicates: outgoing,
      incomingRequiredPredicates: incoming,
      fallback: fallback,
      expansion: fallback ? 'predicate profile fallback' : 'node schema metadata'
    };
  }
  function defaultInformationView() {
    return {
      name: 'cncf-rdf-1.5-hop-information-view-v1',
      label: 'CNCF RDF 1.5+hop Information View',
      concept: '1.5+hop',
      attributes: [
          'information.schema',
          'information.type',
          'information.category',
          'information.identity',
          'information.description',
          'information.links',
          'information.hierarchy',
          'information.provenance',
          'schema.required',
          'schema.directional',
          'schema.expansion'
      ],
      defaultInformationSchema: 'rdf-resource-information-v1',
      informationSchemas: [
        {
          name: 'rdf-resource-information-v1',
          label: 'RDF Resource Information',
          match: {
            nodeTypes: ['uri', 'literal']
          },
          requiredPredicates: ['rdf:type'],
          descriptivePredicates: ['rdfs:label', 'skos:prefLabel', 'schema:name'],
          outgoingRequiredPredicates: [],
          incomingRequiredPredicates: []
        }
      ],
      predicateProfile: defaultPredicateProfile(),
      predicateRoleAttributes: {
        identity: 'information.identity',
        descriptive: 'information.description',
        link: 'information.links',
        hierarchy: 'information.hierarchy',
        provenance: 'information.provenance'
      },
      nodePredicateFields: {
        required: ['requiredPredicates', 'schema.requiredPredicates'],
        descriptive: ['descriptivePredicates', 'schema.descriptivePredicates'],
        outgoing: ['schema.outgoingRequiredPredicates'],
        incoming: ['schema.incomingRequiredPredicates']
      },
      expansion: {
        base: 'direct 1-hop',
        plus: 'schema-required descriptive triples',
        limit: 3
      }
    };
  }
  function defaultPredicateProfile() {
    return {
      name: 'cncf-rdf-1.5-hop-v1',
      roles: {
        identity: [
          'rdf:type',
          'owl:sameAs',
          'schema:sameAs',
          'skos:exactMatch',
          'skos:closeMatch',
          'textus:primaryRdfAnchor'
        ],
        descriptive: [
          'rdfs:label',
          'rdfs:comment',
          'skos:prefLabel',
          'skos:altLabel',
          'skos:definition',
          'schema:name',
          'schema:title',
          'schema:description',
          'schema:summary'
        ],
        link: [
          'rdfs:seeAlso',
          'schema:about',
          'schema:url',
          'schema:memberOf'
        ],
        hierarchy: [
          'skos:broader',
          'skos:narrower',
          'dcterms:isPartOf',
          'dcterms:hasPart',
          'schema:isPartOf',
          'schema:hasPart'
        ],
        provenance: [
          'dcterms:source',
          'prov:wasDerivedFrom',
          'prov:generatedAtTime',
          'rdfs:isDefinedBy'
        ]
      }
    };
  }
  function activePredicateProfile(data) {
    const base = defaultPredicateProfile();
    const configured = (data && data.informationView && data.informationView.predicateProfile) || {};
    const roles = {};
    Object.keys(base.roles).forEach(function(role) {
      roles[role] = configured.roles && Object.prototype.hasOwnProperty.call(configured.roles, role) ?
        uniqueStrings([base.roles[role], configured.roles[role]]) :
        uniqueStrings([base.roles[role]]);
    });
    if (configured.roles) {
      Object.keys(configured.roles).forEach(function(role) {
        if (!roles[role]) roles[role] = uniqueStrings([configured.roles[role]]);
      });
    }
    return {
      name: configured.name || base.name,
      roles: roles
    };
  }
  function activeInformationView(data) {
    const base = defaultInformationView();
    const configured = (data && data.informationView) || {};
    const profile = activePredicateProfile(data);
    const informationSchemas = configured.informationSchemas || configured.information_schemas || base.informationSchemas;
    return {
      name: configured.name || base.name,
      label: configured.label || base.label,
      concept: configured.concept || base.concept,
      attributes: configured.attributes || base.attributes,
      defaultInformationSchema: configured.defaultInformationSchema || configured.default_information_schema || base.defaultInformationSchema,
      informationSchemas: asArray(informationSchemas),
      predicateProfile: profile,
      predicateRoleAttributes: configured.predicateRoleAttributes || base.predicateRoleAttributes,
      nodePredicateFields: configured.nodePredicateFields || base.nodePredicateFields,
      expansion: configured.expansion || base.expansion
    };
  }
  function currentPredicateProfile() {
    return window.__bokRdfPredicateProfile || defaultPredicateProfile();
  }
  function currentInformationView() {
    return window.__bokRdfInformationView || defaultInformationView();
  }
  function profileRolePredicates(profile, role) {
    return uniqueStrings([profile && profile.roles && profile.roles[role]]);
  }
  function profilePredicates(profile) {
    const roles = (profile && profile.roles) || {};
    return uniqueStrings(Object.keys(roles).map(function(role) { return roles[role]; }));
  }
  function predicateRoles(predicates, profile) {
    const roles = (profile && profile.roles) || {};
    const result = [];
    Object.keys(roles).sort().forEach(function(role) {
      const rolePredicates = profileRolePredicates(profile, role);
      const matched = predicates.some(function(predicate) {
        return rolePredicates.some(function(candidate) {
          return predicateKey(predicate) === predicateKey(candidate);
        });
      });
      if (matched) result.push(role);
    });
    return result;
  }
  function uniqueStrings(values) {
    const seen = new Set();
    const result = [];
    values.forEach(function(item) {
      asArray(item).forEach(function(value) {
        const text = String(value || '').trim();
        if (text && !seen.has(text)) {
          seen.add(text);
          result.push(text);
        }
      });
    });
    return result;
  }
  function renderSchemaInterpretation(node) {
    const interpretation = schemaInterpretation(node);
    const sie = node.sie || {};
    return '<div class="bok-rdf-node-schema">' +
      '<strong>${_javascript_string(_ui(locale, "rdf.graph.node.schema"))}</strong>' +
      '<div class="bok-rdf-node-schema-groups">' +
        schemaGroup('informationView', [
          ['name', interpretation.informationViewName],
          ['concept', interpretation.informationViewConcept],
          ['label', interpretation.informationViewLabel],
          ['predicateProfile', interpretation.profile]
        ]) +
        schemaGroup('information', [
          ['schema', interpretation.informationSchema],
          ['schemaLabel', interpretation.informationSchemaLabel],
          ['type', interpretation.informationType],
          ['category', interpretation.category],
          ['identity', interpretation.identityPredicates],
          ['description', interpretation.descriptivePredicates],
          ['links', interpretation.linkPredicates],
          ['hierarchy', interpretation.hierarchyPredicates],
          ['provenance', interpretation.provenancePredicates]
        ]) +
        schemaGroup('monoKoto', [
          ['classification', nodeMonoKotoValues(node)],
          ['cmlLinkage', nodeCmlLinkValues(node)]
        ]) +
        (node.sie ? schemaGroup('sie', [
          ['projection', sie.projection || '-'],
          ['informationId', sie.informationId || sie.information_id || '-'],
          ['termRefs', sie.termRefs || sie.term_refs || []],
          ['scenarioRefs', sie.scenarioRefs || sie.scenario_refs || []],
          ['projectRefs', sie.projectRefs || sie.project_refs || []],
          ['tags', sie.tags || []]
        ]) : '') +
        schemaGroup('schema', [
          ['required', interpretation.requiredPredicates],
          ['nodeDescription', interpretation.nodeDescriptivePredicates],
          ['directional', uniqueStrings([interpretation.outgoingRequiredPredicates, interpretation.incomingRequiredPredicates])],
          ['expansion', interpretation.expansion],
          ['fallback', interpretation.fallback ? 'true' : 'false']
        ]) +
        schemaGroup('predicate', [
          ['roles', interpretation.predicateRoles]
        ]) +
      '</div>' +
    '</div>';
  }
  function schemaGroup(name, entries) {
    return '<section class="bok-rdf-node-schema-group bok-rdf-node-schema-group-' + escapeHtml(cssName(name)) + '">' +
      '<h3>' + escapeHtml(name) + '</h3>' +
      '<dl class="bok-rdf-node-schema-object">' +
        entries.map(function(entry) { return schemaProperty(entry[0], entry[1]); }).join('') +
      '</dl>' +
    '</section>';
  }
  function schemaProperty(name, value) {
    const rendered = Array.isArray(value) ? (value.length ? value.map(function(item) {
      return '<code>' + escapeHtml(item) + '</code>';
    }).join(' ') : '<em>-</em>') : '<code>' + escapeHtml(value) + '</code>';
    return '<dt>' + escapeHtml(name) + '</dt><dd>' + rendered + '</dd>';
  }
  function asArray(value) {
    if (Array.isArray(value)) return value;
    if (value == null) return [];
    return [value];
  }
  function predicateMatches(edge, predicate) {
    const expected = predicateKey(predicate);
    return predicateKey(edge.predicate) === expected || predicateKey(edge.label) === expected;
  }
  function isDefaultDescriptionPredicate(predicate) {
    const key = predicateKey(predicate);
    return profilePredicates(currentPredicateProfile()).some(function(candidate) {
      return predicateKey(candidate) === key;
    });
  }
  function predicateKey(value) {
    return String(value || '').split(/[\\/#:]/).filter(Boolean).pop().toLowerCase().replace(/[^a-z0-9]/g, '');
  }
  function edgeKey(edge) {
    return String(edge.source || '') + '\\n' + String(edge.predicate || edge.label || '') + '\\n' + String(edge.target || '');
  }
  function showNodeDetail(node, edges) {
    const canvas = graphTarget.querySelector('[data-rdf-graph-canvas]');
    if (!canvas || !node) return;
    const monokoto = nodeMonoKotoValues(node);
    const cmllinks = nodeCmlLinkValues(node);
    const sie = node.sie || {};
    let panel = canvas.querySelector('.bok-rdf-node-popover');
    if (!panel) {
      panel = document.createElement('aside');
      panel.setAttribute('class', 'bok-rdf-node-popover');
      panel.setAttribute('role', 'dialog');
      panel.setAttribute('aria-label', '${_javascript_string(_ui(locale, "rdf.graph.node.detail"))}');
      canvas.appendChild(panel);
    }
    const related = edges.filter(function(edge) { return edge.source === node.id || edge.target === node.id; }).slice(0, 6);
    panel.hidden = false;
    panel.innerHTML =
      '<button type="button" class="bok-rdf-node-popover-close" data-rdf-node-popover-close="true" aria-label="${_javascript_string(_ui(locale, "rdf.graph.node.close"))}">×</button>' +
      '<div class="bok-rdf-node-popover-eyebrow">${_javascript_string(_ui(locale, "rdf.graph.node.detail"))}</div>' +
      '<h2>' + escapeHtml(compactNodeLabel(node)) + '</h2>' +
      '<div class="bok-rdf-node-popover-actions bok-rdf-node-popover-actions-primary"><button type="button" data-rdf-neighborhood="true">${_javascript_string(_ui(locale, "rdf.graph.node.neighborhood"))}</button><a href="node.html?id=' + encodeURIComponent(node.id || '') + '">${_javascript_string(_ui(locale, "rdf.graph.node.full.detail"))}</a></div>' +
      '<dl>' +
        '<dt>Compact label</dt><dd class="bok-rdf-node-compact-label" title="' + escapeHtml(compactNodeLabel(node)) + '"><code>' + escapeHtml(compactNodeLabel(node)) + '</code></dd>' +
        '<dt>Full IRI</dt><dd class="bok-rdf-node-full-iri" title="' + escapeHtml(node.id) + '">' + escapeHtml(node.id) + '</dd>' +
        '<dt>Source label</dt><dd>' + escapeHtml(node.label || '-') + '</dd>' +
        '<dt>Category</dt><dd>' + escapeHtml(node.category || '-') + '</dd>' +
        '<dt>Type</dt><dd>' + escapeHtml(node.node_type || node.type || '-') + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "term.analysis.kind"))}</dt><dd>' + escapeHtml(monokoto.length ? monokoto.join(', ') : '-') + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "term.analysis.cml.linkage"))}</dt><dd>' + escapeHtml(cmllinks.length ? cmllinks.join(', ') : '-') + '</dd>' +
        (node.sie ? '<dt>${_javascript_string(_ui(locale, "project.label.sie.projection"))}</dt><dd>' + escapeHtml(sie.projection || '-') + '</dd>' : '') +
        '<dt>${_javascript_string(_ui(locale, "rdf.graph.node.connections"))}</dt><dd>' + escapeHtml(node.degree == null ? '-' : node.degree) + '</dd>' +
      '</dl>' +
      renderSchemaInterpretation(node) +
      '<div class="bok-rdf-node-popover-relations"><strong>${_javascript_string(_ui(locale, "rdf.graph.node.relations"))}</strong><ul>' +
        (related.length ? related.map(function(edge) { return '<li>' + escapeHtml(compactRdfLabel(edge.source)) + ' <b>' + escapeHtml(compactRdfLabel(edge.label || edge.predicate)) + '</b> ' + escapeHtml(compactRdfLabel(edge.target)) + '</li>'; }).join('') : '<li>-</li>') +
      '</ul></div>';
    const close = panel.querySelector('[data-rdf-node-popover-close]');
    if (close) close.addEventListener('click', function() { panel.hidden = true; });
    const focusButton = panel.querySelector('[data-rdf-neighborhood]');
    if (focusButton) focusButton.addEventListener('click', function() {
      focusedNodeId = node.id;
      renderGraph(window.__bokRdfGraphData, input ? input.value.trim() : '', termInput ? termInput.value.trim() : '', tagInput ? tagInput.value.trim() : '', window.__bokRdfTermIndex || {});
    });
  }
  function renderGraphSvg(canvas, nodes, edges, roles, focus) {
    const svgNs = "http://www.w3.org/2000/svg";
    const width = 1120;
    const height = 660;
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute('class', 'bok-rdf-graph-svg');
    svg.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', 'RDF graph');
    const defs = document.createElementNS(svgNs, 'defs');
    const marker = document.createElementNS(svgNs, 'marker');
    marker.setAttribute('id', 'bok-rdf-arrow');
    marker.setAttribute('viewBox', '0 0 10 10');
    marker.setAttribute('refX', '8');
    marker.setAttribute('refY', '5');
    marker.setAttribute('markerWidth', '6');
    marker.setAttribute('markerHeight', '6');
    marker.setAttribute('orient', 'auto-start-reverse');
    const arrow = document.createElementNS(svgNs, 'path');
    arrow.setAttribute('d', 'M 0 0 L 10 5 L 0 10 z');
    arrow.setAttribute('class', 'bok-rdf-graph-arrow');
    marker.appendChild(arrow);
    defs.appendChild(marker);
    svg.appendChild(defs);
    const positions = {};
    const cx = width / 2;
    const cy = height / 2;
    const rx = width * 0.38;
    const ry = height * 0.32;
    nodes.forEach(function(node, index) {
      const angle = nodes.length === 1 ? -Math.PI / 2 : (2 * Math.PI * index / nodes.length) - Math.PI / 2;
      positions[node.id] = {
        x: nodes.length === 1 ? cx : cx + Math.cos(angle) * rx,
        y: nodes.length === 1 ? cy : cy + Math.sin(angle) * ry
      };
    });
    const edgeLayer = document.createElementNS(svgNs, 'g');
    edgeLayer.setAttribute('class', 'bok-rdf-graph-edges');
    edges.forEach(function(edge) {
      const source = positions[edge.source];
      const target = positions[edge.target];
      if (!source || !target) return;
      const line = document.createElementNS(svgNs, 'line');
      line.setAttribute('class', 'bok-rdf-graph-edge');
      line.setAttribute('x1', source.x);
      line.setAttribute('y1', source.y);
      line.setAttribute('x2', target.x);
      line.setAttribute('y2', target.y);
      line.setAttribute('marker-end', 'url(#bok-rdf-arrow)');
      edgeLayer.appendChild(line);
      const text = document.createElementNS(svgNs, 'text');
      text.setAttribute('class', 'bok-rdf-graph-edge-label');
      text.setAttribute('x', (source.x + target.x) / 2);
      text.setAttribute('y', (source.y + target.y) / 2 - 6);
      text.textContent = compactRdfLabel(edge.label || edge.predicate || 'related');
      edgeLayer.appendChild(text);
    });
    svg.appendChild(edgeLayer);
    const nodeLayer = document.createElementNS(svgNs, 'g');
    nodeLayer.setAttribute('class', 'bok-rdf-graph-nodes');
    nodes.forEach(function(node) {
      const point = positions[node.id];
      const group = document.createElementNS(svgNs, 'g');
      const role = roles[node.id] || 'normal';
      group.setAttribute('class', 'bok-rdf-graph-node bok-rdf-graph-node-' + cssName(node.node_type || node.type || 'unknown') + ' bok-rdf-graph-node-role-' + role);
      group.setAttribute('transform', 'translate(' + point.x + ' ' + point.y + ')');
      group.setAttribute('data-rdf-focus-node', node.id);
      group.setAttribute('tabindex', '0');
      group.setAttribute('role', 'button');
      const circle = document.createElementNS(svgNs, 'circle');
      circle.setAttribute('r', Math.max(18, Math.min(34, 18 + (node.degree || 0) * 2)));
      const title = document.createElementNS(svgNs, 'title');
      title.textContent = compactNodeLabel(node) + ' / ' + (node.category || '-') + ' / ' + String(node.id || '');
      const text = document.createElementNS(svgNs, 'text');
      text.setAttribute('y', 48);
      text.textContent = compactNodeLabel(node);
      group.appendChild(title);
      group.appendChild(circle);
      group.appendChild(text);
      group.addEventListener('click', function() { showNodeDetail(node, edges); });
      group.addEventListener('keydown', function(event) { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); showNodeDetail(node, edges); } });
      nodeLayer.appendChild(group);
    });
    svg.appendChild(nodeLayer);
    canvas.innerHTML = '';
    canvas.appendChild(svg);
  }
  function renderTriples(text) {
       |    if (!triplesTarget || !triplesStatus) return;
       |    triplesTarget.textContent = text;
       |    triplesStatus.textContent = text.split('\\n').filter(function(line) { return line.trim(); }).length + ' lines';
       |  }
       |  function label(value) {
       |    const parts = String(value || '').split(/[\\/#]/).filter(Boolean);
       |    return parts.length ? parts[parts.length - 1] : value;
       |  }
       |  function compactNodeLabel(node) {
       |    if (!node) return '';
       |    return compactRdfLabel(node.id || node.label || '');
       |  }
       |  function compactRdfLabel(value) {
       |    const text = String(value || '');
       |    const compact = compactUri(text);
       |    if (compact) return compact;
       |    return label(text);
       |  }
       |  function compactUri(value) {
       |    const text = String(value || '');
       |    const prefixes = rdfNamespacePrefixes();
       |    for (let i = 0; i < prefixes.length; i += 1) {
       |      if (text.indexOf(prefixes[i][1]) === 0) {
       |        const local = text.substring(prefixes[i][1].length);
       |        return local ? prefixes[i][0] + ':' + local : prefixes[i][0] + ':';
       |      }
       |    }
       |    const compact = text.match(/^([A-Za-z][A-Za-z0-9_-]*):(.+)$$/);
       |    return compact && !/^https?:/.test(text) ? text : '';
       |  }
       |  function rdfNamespacePrefixes() {
       |    return [
       |      ['rdf', 'http://www.w3.org/1999/02/22-rdf-syntax-ns#'],
       |      ['rdfs', 'http://www.w3.org/2000/01/rdf-schema#'],
       |      ['owl', 'http://www.w3.org/2002/07/owl#'],
       |      ['xsd', 'http://www.w3.org/2001/XMLSchema#'],
       |      ['skos', 'http://www.w3.org/2004/02/skos/core#'],
       |      ['dcterms', 'http://purl.org/dc/terms/'],
       |      ['prov', 'http://www.w3.org/ns/prov#'],
       |      ['schema', 'https://schema.org/'],
       |      ['bok', 'https://www.simplemodeling.org/bok/'],
       |      ['cozy-video', 'https://www.simplemodeling.org/ns/cozy/video#'],
       |      ['textus', 'https://www.simplemodeling.org/ns/textus#'],
       |      ['sm', 'https://www.simplemodeling.org/']
       |    ];
       |  }
       |  function escapeHtml(value) {
       |    return String(value == null ? '' : value).replace(/[&<>"']/g, function(c) {
       |      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
       |    });
       |  }
       |  function cssName(value) {
       |    return String(value == null ? 'unknown' : value).toLowerCase().replace(/[^a-z0-9_-]+/g, '-');
       |  }
       |  const graphPromise = fetch(root.getAttribute('data-graph')).then(function(response) {
       |    if (!response.ok) throw new Error('missing graph metadata');
       |    return response.json();
       |  });
       |  const termsPromise = fetch(root.getAttribute('data-terms')).then(function(response) {
       |    if (!response.ok) return {terms: []};
       |    return response.json();
       |  }).catch(function() { return {terms: []}; });
       |  Promise.all([graphPromise, termsPromise]).then(function(results) {
       |    const data = results[0];
       |    const terms = results[1];
       |    const termIndex = {};
       |    (terms.terms || []).forEach(function(term) {
       |      if (term && term.id) termIndex[term.id] = term;
       |    });
       |    window.__bokRdfGraphData = data;
       |    window.__bokRdfTermIndex = termIndex;
       |    window.__bokRdfPredicateProfile = activePredicateProfile(data);
       |    window.__bokRdfInformationView = activeInformationView(data);
       |    renderGraph(data, initialCategory, initialTerm, initialTag, termIndex);
       |    function refresh() { focusedNodeId = null; renderGraph(data, input ? input.value.trim() : '', termInput ? termInput.value.trim() : '', tagInput ? tagInput.value.trim() : '', termIndex); }
       |    if (input) input.addEventListener('input', refresh);
       |    if (termInput) termInput.addEventListener('input', refresh);
       |    if (tagInput) tagInput.addEventListener('input', refresh);
       |  }).catch(function() {
       |    graphStatus.textContent = '${_javascript_string(_ui(locale, "rdf.graph.metadata.missing"))}';
       |    graphTarget.innerHTML = '<div class="bok-rdf-empty">${_javascript_string(_ui(locale, "rdf.graph.metadata.missing"))}</div>';
       |  });
       |  if (triplesTarget) {
       |    fetch(root.getAttribute('data-triples')).then(function(response) {
       |      if (!response.ok) throw new Error('missing RDF triples');
       |      return response.text();
       |    }).then(renderTriples).catch(function() {
       |      if (triplesStatus) triplesStatus.textContent = '${_javascript_string(_ui(locale, "rdf.graph.triples.missing"))}';
       |      triplesTarget.textContent = '';
       |    });
       |  }
       |}());""".stripMargin


}
