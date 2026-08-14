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

private[cozy] trait CozyBokRdfPages {
  self: CozyBokImplementation.type =>
  private[bok] def _rdf_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "rdf.graph.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-rdf-body">
       |  <main class="article bok-rdf-main">
       |    <div class="content">
       |      <article class="doc bok-rdf-doc">
       |        ${_rdf_workspace(locale)}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private[bok] def _rdf_node_detail_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "rdf.graph.node.full.detail"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-rdf-body">
       |  <main class="article bok-rdf-main">
       |    <div class="content">
       |      <article class="doc bok-rdf-doc">
       |        ${_rdf_node_detail_workspace(locale)}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _rdf_node_detail_workspace(locale: String): String =
    s"""<section class="bok-rdf-node-page" data-graph="../metadata/rdf/graph.json" data-terms="../metadata/glossary/terms.json">
       |  <div class="bok-rdf-hero">
       |    <div>
       |      <span class="bok-dashboard-eyebrow">RDF</span>
       |      <h1 class="page">${_html_escape(_ui(locale, "rdf.graph.node.full.detail"))}</h1>
       |      <p>${_html_escape(_ui(locale, "rdf.graph.node.full.description"))}</p>
       |    </div>
       |    <div class="bok-rdf-hero-actions">
       |      <a href="index.html">${_html_escape(_ui(locale, "rdf.graph.title"))}</a>
       |      <a href="site.ttl">site.ttl</a>
       |      <a href="site.jsonld">site.jsonld</a>
       |      <a href="../metadata/rdf/graph.json">graph.json</a>
       |    </div>
       |  </div>
       |  <div id="bok-rdf-node-page-status" class="bok-rdf-node-page-status">${_html_escape(_ui(locale, "rdf.graph.loading"))}</div>
       |  <div id="bok-rdf-node-page-content" class="bok-rdf-node-page-content"></div>
       |</section>
       |<script>
       |${_rdf_node_detail_script(locale)}
       |</script>""".stripMargin

  private def _rdf_workspace(locale: String): String =
    s"""<section class="bok-rdf-workspace" data-graph="../metadata/rdf/graph.json" data-terms="../metadata/glossary/terms.json" data-triples="site.ttl">
       |  <div class="bok-rdf-hero">
       |    <div>
       |      <span class="bok-dashboard-eyebrow">RDF</span>
       |      <h1 class="page">${_html_escape(_ui(locale, "rdf.graph.title"))}</h1>
       |      <p>${_html_escape(_ui(locale, "rdf.graph.description"))}</p>
       |    </div>
       |    <div class="bok-rdf-hero-actions">
       |      <a href="../index.html">${_html_escape(_ui(locale, "nav.home"))}</a>
       |      <a href="site.ttl">site.ttl</a>
       |      <a href="site.jsonld">site.jsonld</a>
       |      <a href="../metadata/rdf/graph.json">graph.json</a>
       |    </div>
       |  </div>
       |  <div class="bok-rdf-tabbar">
       |    <div class="bok-rdf-view-switch" role="tablist" aria-label="RDF views">
       |      <button class="is-active" type="button" role="tab" aria-selected="true" aria-controls="bok-rdf-panel-graph" data-rdf-view="graph">${_html_escape(_ui(locale, "rdf.graph.view.graph"))}</button>
       |      <button type="button" role="tab" aria-selected="false" aria-controls="bok-rdf-panel-nodes" data-rdf-view="nodes">${_html_escape(_ui(locale, "rdf.graph.view.nodes"))}</button>
       |      <button type="button" role="tab" aria-selected="false" aria-controls="bok-rdf-panel-triples" data-rdf-view="triples">${_html_escape(_ui(locale, "rdf.graph.view.triples"))}</button>
       |    </div>
       |  </div>
       |  <div class="bok-rdf-filterbar">
       |    <label>${_html_escape(_ui(locale, "rdf.graph.category.filter"))}<input id="bok-rdf-category-filter" type="text" placeholder="category"></label>
       |    <label>${_html_escape(_ui(locale, "rdf.graph.term.filter"))}<input id="bok-rdf-term-filter" type="text" placeholder="term"></label>
       |    <label>${_html_escape(_ui(locale, "tag.title"))}<input id="bok-rdf-tag-filter" type="text" placeholder="tag"></label>
       |    <span id="bok-rdf-viewer-status">${_html_escape(_ui(locale, "rdf.graph.loading"))}</span>
       |  </div>
       |  <div class="bok-rdf-panels">
       |    <section id="bok-rdf-panel-graph" class="bok-rdf-panel bok-rdf-panel-graph is-active" data-rdf-panel="graph" role="tabpanel" aria-label="${_html_escape(_ui(locale, "rdf.graph.view.graph"))}">
       |      <h2 class="bok-rdf-panel-title">${_html_escape(_ui(locale, "rdf.graph.view.graph"))}</h2>
       |      <div id="bok-rdf-viewer-graph" class="bok-rdf-viewer-graph"></div>
       |    </section>
       |    <section id="bok-rdf-panel-nodes" class="bok-rdf-panel bok-rdf-panel-nodes" data-rdf-panel="nodes" role="tabpanel" aria-label="${_html_escape(_ui(locale, "rdf.graph.view.nodes"))}">
       |      <h2 class="bok-rdf-panel-title">${_html_escape(_ui(locale, "rdf.graph.view.nodes"))}</h2>
       |      <div id="bok-rdf-node-list-view" class="bok-rdf-node-list-view"></div>
       |    </section>
       |    <section id="bok-rdf-panel-triples" class="bok-rdf-panel bok-rdf-panel-triples" data-rdf-panel="triples" role="tabpanel" aria-label="${_html_escape(_ui(locale, "rdf.graph.view.triples"))}">
       |      <div class="bok-rdf-triples-header">
       |        <strong>${_html_escape(_ui(locale, "rdf.graph.triples.title"))}</strong>
       |        <span id="bok-rdf-triples-status">${_html_escape(_ui(locale, "rdf.graph.triples.loading"))}</span>
       |      </div>
       |      <pre id="bok-rdf-triples-view" class="bok-rdf-triples-view"></pre>
       |    </section>
       |  </div>
       |</section>
       |<script>
       |${_rdf_viewer_script(locale)}
       |</script>""".stripMargin

  private def _rdf_node_detail_script(locale: String): String =
    s"""(function() {
       |  const root = document.querySelector('.bok-rdf-node-page');
       |  const status = document.getElementById('bok-rdf-node-page-status');
       |  const content = document.getElementById('bok-rdf-node-page-content');
       |  if (!root || !status || !content) return;
       |  const params = new URLSearchParams(window.location.search);
       |  const nodeId = params.get('id') || params.get('node') || '';
       |  let termIndex = {};
       |  function escapeHtml(value) {
       |    return String(value == null ? '' : value).replace(/[&<>"']/g, function(c) {
       |      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
       |    });
       |  }
       |  function compactRdfLabel(value) {
       |    const text = String(value || '');
       |    const prefixes = rdfNamespacePrefixes();
       |    for (let i = 0; i < prefixes.length; i += 1) {
       |      const prefix = prefixes[i][0];
       |      const iri = prefixes[i][1];
       |      if (text.indexOf(iri) === 0) return prefix + ':' + text.substring(iri.length);
       |    }
       |    const parts = text.split(/[\\/#]/).filter(Boolean);
       |    return parts.length ? parts[parts.length - 1] : text;
       |  }
       |  function compactNodeLabel(node) {
       |    const label = String((node && node.label) || '').trim();
       |    const id = String((node && node.id) || '').trim();
       |    if (id && (label === id || label.indexOf('http://') === 0 || label.indexOf('https://') === 0)) return compactRdfLabel(id);
       |    return label || compactRdfLabel(id) || '-';
       |  }
       |  function rdfNamespacePrefixes() {
       |    return [
       |      ['rdf', 'http://www.w3.org/1999/02/22-rdf-syntax-ns#'],
       |      ['rdfs', 'http://www.w3.org/2000/01/rdf-schema#'],
       |      ['owl', 'http://www.w3.org/2002/07/owl#'],
       |      ['skos', 'http://www.w3.org/2004/02/skos/core#'],
       |      ['dcterms', 'http://purl.org/dc/terms/'],
       |      ['schema', 'https://schema.org/'],
       |      ['prov', 'http://www.w3.org/ns/prov#'],
       |      ['textus', 'https://www.simplemodeling.org/ns/textus#'],
       |      ['bok', 'https://www.simplemodeling.org/bok/']
       |    ];
       |  }
       |  function asArray(value) {
       |    if (Array.isArray(value)) return value;
       |    if (value == null) return [];
       |    return [value];
       |  }
       |  function uniqueStrings(values) {
       |    const seen = new Set();
       |    const result = [];
       |    values.forEach(function(item) {
       |      asArray(item).forEach(function(value) {
       |        const text = String(value || '').trim();
       |        if (text && !seen.has(text)) { seen.add(text); result.push(text); }
       |      });
       |    });
       |    return result;
       |  }
       |  function nodeTerms(node) {
       |    return asArray((node && node.terms) || []).map(function(term) { return termIndex[term]; }).filter(Boolean);
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
       |  function defaultPredicateProfile() {
       |    return {
       |      name: 'cncf-rdf-1.5-hop-v1',
       |      roles: {
       |        identity: ['rdf:type', 'owl:sameAs', 'schema:sameAs', 'skos:exactMatch', 'skos:closeMatch', 'textus:primaryRdfAnchor'],
       |        descriptive: ['rdfs:label', 'rdfs:comment', 'skos:prefLabel', 'skos:altLabel', 'skos:definition', 'schema:name', 'schema:title', 'schema:description', 'schema:summary'],
       |        link: ['rdfs:seeAlso', 'schema:about', 'schema:url', 'schema:memberOf'],
       |        hierarchy: ['skos:broader', 'skos:narrower', 'dcterms:isPartOf', 'dcterms:hasPart', 'schema:isPartOf', 'schema:hasPart'],
       |        provenance: ['dcterms:source', 'prov:wasDerivedFrom', 'prov:generatedAtTime', 'rdfs:isDefinedBy']
       |      }
       |    };
       |  }
       |  function activePredicateProfile(data) {
       |    const base = defaultPredicateProfile();
       |    const configured = (data && data.informationView && data.informationView.predicateProfile) || {};
       |    const roles = {};
       |    Object.keys(base.roles).forEach(function(role) {
       |      roles[role] = configured.roles && Object.prototype.hasOwnProperty.call(configured.roles, role) ?
       |        uniqueStrings([base.roles[role], configured.roles[role]]) : uniqueStrings([base.roles[role]]);
       |    });
       |    if (configured.roles) {
       |      Object.keys(configured.roles).forEach(function(role) {
       |        if (!roles[role]) roles[role] = uniqueStrings([configured.roles[role]]);
       |      });
       |    }
       |    return {name: configured.name || base.name, roles: roles};
       |  }
       |  function defaultInformationView() {
       |    return {
       |      name: 'cncf-rdf-1.5-hop-information-view-v1',
       |      label: 'CNCF RDF 1.5+hop Information View',
       |      concept: '1.5+hop',
       |      informationSchemas: [{
       |        name: 'rdf-resource-information-v1',
       |        label: 'RDF Resource Information',
       |        match: {nodeTypes: ['uri', 'literal']},
       |        requiredPredicates: ['rdf:type'],
       |        descriptivePredicates: ['rdfs:label', 'skos:prefLabel', 'schema:name'],
       |        outgoingRequiredPredicates: [],
       |        incomingRequiredPredicates: []
       |      }],
       |      predicateProfile: defaultPredicateProfile()
       |    };
       |  }
       |  function activeInformationView(data) {
       |    const base = defaultInformationView();
       |    const configured = (data && data.informationView) || {};
       |    return {
       |      name: configured.name || base.name,
       |      label: configured.label || base.label,
       |      concept: configured.concept || base.concept,
       |      informationSchemas: asArray(configured.informationSchemas || configured.information_schemas || base.informationSchemas),
       |      predicateProfile: activePredicateProfile(data)
       |    };
       |  }
       |  function selectedInformationSchema(node, informationView) {
       |    const candidates = asArray(informationView.informationSchemas);
       |    const safeNode = node || {};
       |    const schema = safeNode.schema || {};
       |    const requested = String(schema.informationSchema || schema.information_schema || safeNode.informationSchema || safeNode.information_schema || '').trim();
       |    if (requested) {
       |      const direct = candidates.filter(function(item) { return String(item.name || item.id || '') === requested || String(item.label || '') === requested; })[0];
       |      if (direct) return direct;
       |    }
       |    const nodeType = String(safeNode.informationType || safeNode.information_type || safeNode.node_type || safeNode.type || '').trim();
       |    const category = String(safeNode.category || '').trim();
       |    const matched = candidates.filter(function(item) {
       |      const match = item.match || {};
       |      const nodeTypes = uniqueStrings([item.nodeTypes, item.node_types, match.nodeTypes, match.node_types]);
       |      const categories = uniqueStrings([item.categories, match.categories]);
       |      const typeOk = nodeTypes.length === 0 || nodeTypes.indexOf(nodeType) >= 0;
       |      const categoryOk = categories.length === 0 || categories.indexOf(category) >= 0;
       |      return typeOk && categoryOk;
       |    })[0];
       |    return matched || candidates[0] || {};
       |  }
       |  function informationSchemaPredicates(informationSchema, kind) {
       |    if (!informationSchema) return [];
       |    const schema = informationSchema.schema || {};
       |    const fields = {
       |      required: [informationSchema.requiredPredicates, informationSchema.required_predicates, schema.requiredPredicates, schema.required_predicates],
       |      descriptive: [informationSchema.descriptivePredicates, informationSchema.descriptive_predicates, schema.descriptivePredicates, schema.descriptive_predicates],
       |      outgoing: [informationSchema.outgoingRequiredPredicates, informationSchema.outgoing_required_predicates, schema.outgoingRequiredPredicates, schema.outgoing_required_predicates],
       |      incoming: [informationSchema.incomingRequiredPredicates, informationSchema.incoming_required_predicates, schema.incomingRequiredPredicates, schema.incoming_required_predicates]
       |    };
       |    return uniqueStrings(fields[kind] || []);
       |  }
       |  function profileRolePredicates(profile, role) {
       |    return uniqueStrings([profile && profile.roles && profile.roles[role]]);
       |  }
       |  function interpretation(node, data) {
       |    const schema = node.schema || {};
       |    const view = activeInformationView(data);
       |    const profile = view.predicateProfile || activePredicateProfile(data);
       |    const informationSchema = selectedInformationSchema(node, view);
       |    const required = uniqueStrings([informationSchemaPredicates(informationSchema, 'required'), node.requiredPredicates, node.required_predicates, schema.requiredPredicates, schema.required_predicates]);
       |    const descriptive = uniqueStrings([informationSchemaPredicates(informationSchema, 'descriptive'), node.descriptivePredicates, node.descriptive_predicates, schema.descriptivePredicates, schema.descriptive_predicates]);
       |    const outgoing = uniqueStrings([informationSchemaPredicates(informationSchema, 'outgoing'), schema.outgoingRequiredPredicates, schema.outgoing_required_predicates]);
       |    const incoming = uniqueStrings([informationSchemaPredicates(informationSchema, 'incoming'), schema.incomingRequiredPredicates, schema.incoming_required_predicates]);
       |    const result = {
       |      informationView: {
       |        name: view.name || '-',
       |        concept: view.concept || '1.5+hop',
       |        label: view.label || '-',
       |        predicateProfile: profile.name || 'cncf-rdf-1.5-hop-v1'
       |      },
       |      information: {
       |        schema: informationSchema.name || informationSchema.id || 'rdf-resource-information-v1',
       |        schemaLabel: informationSchema.label || informationSchema.name || informationSchema.id || 'RDF Resource Information',
       |        type: node.informationType || node.information_type || node.node_type || node.type || 'unknown',
       |        category: node.category || '-',
       |        identity: profileRolePredicates(profile, 'identity'),
       |        description: uniqueStrings([profileRolePredicates(profile, 'descriptive'), descriptive]),
       |        links: profileRolePredicates(profile, 'link'),
       |        hierarchy: profileRolePredicates(profile, 'hierarchy'),
       |        provenance: profileRolePredicates(profile, 'provenance')
       |      },
       |      monoKoto: {
       |        classification: nodeMonoKotoValues(node),
       |        cmlLinkage: nodeCmlLinkValues(node)
       |      },
       |      schema: {
       |        required: required,
       |        nodeDescription: descriptive,
       |        directional: uniqueStrings([outgoing, incoming]),
       |        expansion: required.length + descriptive.length + outgoing.length + incoming.length === 0 ? 'predicate profile fallback' : 'node schema metadata'
       |      },
       |      predicate: {
       |        roles: Object.keys(profile.roles || {}).sort()
       |      }
       |    };
       |    if (node.sie) {
       |      result.sie = {
       |        projection: node.sie.projection || '-',
       |        informationId: node.sie.informationId || node.sie.information_id || '-',
       |        termRefs: node.sie.termRefs || node.sie.term_refs || [],
       |        scenarioRefs: node.sie.scenarioRefs || node.sie.scenario_refs || [],
       |        projectRefs: node.sie.projectRefs || node.sie.project_refs || [],
       |        tags: node.sie.tags || []
       |      };
       |    }
       |    return result;
       |  }
       |  function cssName(value) {
       |    return String(value == null ? 'unknown' : value).toLowerCase().replace(/[^a-z0-9_-]+/g, '-');
       |  }
       |  function propertyList(items) {
       |    return Object.keys(items).map(function(key) {
       |      const value = items[key];
       |      if (value && typeof value === 'object' && !Array.isArray(value)) {
       |        return '<section class="bok-rdf-node-schema-group bok-rdf-node-schema-group-' + escapeHtml(cssName(key)) + '">' +
       |          '<h3>' + escapeHtml(key) + '</h3>' +
       |          '<dl class="bok-rdf-node-schema-object">' + propertyList(value) + '</dl>' +
       |        '</section>';
       |      }
       |      const rendered = Array.isArray(value) ? (value.length ? value.map(function(x) { return '<code>' + escapeHtml(x) + '</code>'; }).join(' ') : '<em>-</em>') : '<code>' + escapeHtml(value) + '</code>';
       |      return '<dt>' + escapeHtml(key) + '</dt><dd>' + rendered + '</dd>';
       |    }).join('');
       |  }
       |  function edgeItem(edge) {
       |    const direction = edge.source === nodeId ? 'outgoing' : (edge.target === nodeId ? 'incoming' : 'related');
       |    return '<li><span>' + escapeHtml(direction) + '</span><code title="' + escapeHtml(edge.source || '') + '">' + escapeHtml(compactRdfLabel(edge.source)) + '</code> <b>' + escapeHtml(compactRdfLabel(edge.label || edge.predicate)) + '</b> <code title="' + escapeHtml(edge.target || '') + '">' + escapeHtml(compactRdfLabel(edge.target)) + '</code></li>';
       |  }
       |  function render(data) {
       |    if (!nodeId) {
       |      status.textContent = '${_javascript_string(_ui(locale, "rdf.graph.node.missing.id"))}';
       |      return;
       |    }
       |    const nodes = data.nodes || [];
       |    const edges = data.edges || [];
       |    const node = nodes.filter(function(item) { return item.id === nodeId; })[0];
       |    if (!node) {
       |      status.textContent = '${_javascript_string(_ui(locale, "rdf.graph.node.not.found"))}';
       |      content.innerHTML = '<p class="bok-rdf-empty"><code>' + escapeHtml(nodeId) + '</code></p>';
       |      return;
       |    }
       |    const related = edges.filter(function(edge) { return edge.source === node.id || edge.target === node.id; });
       |    const monokoto = nodeMonoKotoValues(node);
       |    const cmllinks = nodeCmlLinkValues(node);
       |    const sie = node.sie || {};
       |    status.textContent = compactNodeLabel(node) + ' / ' + related.length + ' ${_javascript_string(_ui(locale, "rdf.graph.node.connections"))}';
       |    content.innerHTML =
       |      '<div class="bok-rdf-node-detail-grid">' +
       |        '<section class="bok-rdf-node-detail-card bok-rdf-node-detail-card-main">' +
       |          '<span class="bok-dashboard-eyebrow">Information</span>' +
       |          '<h2>' + escapeHtml(compactNodeLabel(node)) + '</h2>' +
       |          '<dl class="bok-rdf-node-detail-dl">' +
       |            '<dt>Compact label</dt><dd><code>' + escapeHtml(compactNodeLabel(node)) + '</code></dd>' +
       |            '<dt>Full IRI</dt><dd class="bok-rdf-node-full-iri" title="' + escapeHtml(node.id) + '">' + escapeHtml(node.id) + '</dd>' +
       |            '<dt>Source label</dt><dd>' + escapeHtml(node.label || '-') + '</dd>' +
       |            '<dt>Category</dt><dd>' + escapeHtml(node.category || '-') + '</dd>' +
       |            '<dt>Type</dt><dd>' + escapeHtml(node.node_type || node.type || '-') + '</dd>' +
       |            '<dt>${_javascript_string(_ui(locale, "term.analysis.kind"))}</dt><dd>' + escapeHtml(monokoto.length ? monokoto.join(', ') : '-') + '</dd>' +
       |            '<dt>${_javascript_string(_ui(locale, "term.analysis.cml.linkage"))}</dt><dd>' + escapeHtml(cmllinks.length ? cmllinks.join(', ') : '-') + '</dd>' +
       |            (node.sie ? '<dt>${_javascript_string(_ui(locale, "project.label.sie.projection"))}</dt><dd>' + escapeHtml(sie.projection || '-') + '</dd>' : '') +
       |            '<dt>${_javascript_string(_ui(locale, "rdf.graph.node.connections"))}</dt><dd>' + escapeHtml(node.degree == null ? related.length : node.degree) + '</dd>' +
       |          '</dl>' +
       |          '<div class="bok-rdf-node-detail-actions"><a href="index.html?node=' + encodeURIComponent(node.id || '') + '">${_javascript_string(_ui(locale, "rdf.graph.node.neighborhood"))}</a><a href="index.html">${_javascript_string(_ui(locale, "rdf.graph.title"))}</a></div>' +
       |        '</section>' +
       |        '<section class="bok-rdf-node-detail-card"><h2>${_javascript_string(_ui(locale, "rdf.graph.node.schema"))}</h2><div class="bok-rdf-node-schema-groups">' + propertyList(interpretation(node, data)) + '</div></section>' +
       |        '<section class="bok-rdf-node-detail-card bok-rdf-node-detail-card-relations"><h2>${_javascript_string(_ui(locale, "rdf.graph.node.relations"))}</h2><ul>' + (related.length ? related.map(edgeItem).join('') : '<li>-</li>') + '</ul></section>' +
       |      '</div>';
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
       |    const terms = results[1];
       |    (terms.terms || []).forEach(function(term) {
       |      if (term && term.id) termIndex[term.id] = term;
       |    });
       |    render(results[0]);
       |  }).catch(function() {
       |    status.textContent = '${_javascript_string(_ui(locale, "rdf.graph.metadata.missing"))}';
       |  });
       |})();""".stripMargin

}
