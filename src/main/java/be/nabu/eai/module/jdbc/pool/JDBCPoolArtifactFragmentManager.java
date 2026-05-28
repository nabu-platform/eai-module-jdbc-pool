package be.nabu.eai.module.jdbc.pool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.Driver;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.CreatableArtifactFragmentManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.impl.DefinedServiceArtifactFragmentManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.services.DefinedServiceInterfaceResolverFactory;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.services.pojo.POJOUtils;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;

public class JDBCPoolArtifactFragmentManager extends DefinedServiceArtifactFragmentManager<JDBCPoolArtifact> implements CreatableArtifactFragmentManager<JDBCPoolArtifact> {

	private static final String JDBC_CONNECTION_PATH = "jdbc-connection.xml";
	private static final String ARTIFACT_RESOURCE_PATH = "jdbcPool.xml";
	private static final String CONTENT_TYPE = "application/xml";
	private static final String ARTIFACT_TYPE = "jdbcPool";
	private static final String ARTIFACT_CATEGORY = "service";
	private static final String GUIDELINES_PATH = "/guidelines/jdbc-pool.md";
	private static final String PASSWORD = "password";
	private static final String ENABLE_METRICS = "enableMetrics";
	private static final String KNOWN_DRIVERS_PLACEHOLDER = "{{KNOWN_DRIVERS}}";
	private static final String KNOWN_DIALECTS_PLACEHOLDER = "{{KNOWN_DIALECTS}}";

	@Override
	public Entry createArtifact(Entry parent, String name) {
		try {
			RepositoryEntry entry = ((RepositoryEntry) parent).createNode(name, new JDBCPoolManager(), true);
			JDBCPoolArtifact artifact = new JDBCPoolArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
			new JDBCPoolManager().save(entry, artifact);
			return entry;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<ArtifactFragment> listFragments(final JDBCPoolArtifact artifact) {
		List<ArtifactFragment> fragments = new ArrayList<ArtifactFragment>(super.listFragments(artifact));
		fragments.add(new ArtifactFragment() {
			@Override
			public boolean isEditable() {
				return EAIResourceRepository.getInstance().getEntry(artifact.getId()) instanceof ResourceEntry;
			}

			@Override
			public boolean isRemovable() {
				return false;
			}

			@Override
			public String getPath() {
				return JDBC_CONNECTION_PATH;
			}

			@Override
			public String getContent() {
				try {
					return marshalFragment(artifact);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public String getContentType() {
				return CONTENT_TYPE;
			}

			@Override
			public String getArtifactId() {
				return artifact.getId();
			}

			@Override
			public String getFragmentType() {
				return ARTIFACT_TYPE;
			}

			@Override
			public Map<String, String> getProperties() {
				return new LinkedHashMap<String, String>();
			}

			@Override
			public Long getLastModified() {
				return getFragmentLastModified(artifact.getId(), ARTIFACT_RESOURCE_PATH);
			}
		});
		return fragments;
	}

	@Override
	public List<Validation<?>> updateFragment(JDBCPoolArtifact artifact, String path, String oldContent, String newContent) {
		if (!JDBC_CONNECTION_PATH.equals(path)) {
			return super.updateFragment(artifact, path, oldContent, newContent);
		}
		ResourceEntry entry = (ResourceEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId());
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		try {
			String mergedContent = mergeRetainedFields(artifact, newContent);
			JDBCPoolArtifact candidate = new JDBCPoolArtifact(artifact.getId(), entry.getContainer(), entry.getRepository());
			candidate.setConfig(candidate.unmarshal(new ByteArrayInputStream(mergedContent.getBytes(StandardCharsets.UTF_8))));
			validateConfiguration(candidate.getConfig(), validations);
			if (!hasErrors(validations)) {
				validations.addAll(new JDBCPoolManager().save(entry, candidate));
				if (!hasErrors(validations)) {
					artifact.setConfig(candidate.getConfig());
				}
			}
		}
		catch (Exception e) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
		}
		return validations;
	}

	@Override
	public String getGuidelines(List<String> fragmentTypes) {
		List<String> sections = new ArrayList<String>();
		String guidelines = loadGuidelinesResource(GUIDELINES_PATH);
		if (guidelines != null) {
			guidelines = guidelines.replace(KNOWN_DRIVERS_PLACEHOLDER, buildDynamicDriverGuidelines());
			guidelines = guidelines.replace(KNOWN_DIALECTS_PLACEHOLDER, buildDynamicDialectGuidelines());
			sections.add(guidelines.trim());
		}
		String serviceGuidance = super.getGuidelines(fragmentTypes);
		if (serviceGuidance != null && !serviceGuidance.trim().isEmpty()) {
			sections.add(serviceGuidance.trim());
		}
		return String.join("\n\n", sections).trim();
	}

	@Override
	public Class<JDBCPoolArtifact> getArtifactClass() {
		return JDBCPoolArtifact.class;
	}

	@Override
	public String getArtifactType() {
		return ARTIFACT_TYPE;
	}

	@Override
	public String getArtifactCategory() {
		return ARTIFACT_CATEGORY;
	}

	private String marshalFragment(JDBCPoolArtifact artifact) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		artifact.marshal(artifact.getConfig(), output);
		Document document = parseDocument(new String(output.toByteArray(), StandardCharsets.UTF_8));
		removeDirectChild(document.getDocumentElement(), PASSWORD);
		removeDirectChild(document.getDocumentElement(), ENABLE_METRICS);
		return toXml(document);
	}

	private String mergeRetainedFields(JDBCPoolArtifact artifact, String content) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		artifact.marshal(artifact.getConfig(), output);
		Document current = parseDocument(new String(output.toByteArray(), StandardCharsets.UTF_8));
		Document updated = parseDocument(content);
		Element currentRoot = current.getDocumentElement();
		Element updatedRoot = updated.getDocumentElement();
		retainField(currentRoot, updatedRoot, updated, PASSWORD);
		retainField(currentRoot, updatedRoot, updated, ENABLE_METRICS);
		return toXml(updated);
	}

	private void validateConfiguration(JDBCPoolConfiguration configuration, List<Validation<?>> validations) {
		validatePoolProxy(configuration, validations);
		validateConfiguredService(configuration.getTranslationGet(), "translationGet", "be.nabu.libs.services.jdbc.api.JDBCTranslator.get", validations);
		validateConfiguredService(configuration.getTranslationSet(), "translationSet", "be.nabu.libs.services.jdbc.api.JDBCTranslator.set", validations);
		validateConfiguredService(configuration.getTranslationGetBinding(), "translationGetBinding", "be.nabu.libs.services.jdbc.api.JDBCTranslator.getBinding", validations);
		validateConfiguredService(configuration.getTranslationMapLanguage(), "translationMapLanguage", "be.nabu.libs.services.jdbc.api.JDBCTranslator.mapLanguage", validations);
		validateDriver(configuration.getDriverClassName(), validations);
		validateDialect(configuration.getDialect(), validations);
		validatePoolSizing(configuration, validations);
	}

	private void validateConfiguredService(DefinedService service, String fieldName, String interfaceName, List<Validation<?>> validations) {
		if (service == null) {
			return;
		}
		DefinedServiceInterface iface = DefinedServiceInterfaceResolverFactory.getInstance().getResolver().resolve(interfaceName);
		if (iface == null) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Unknown interface requested for '" + fieldName + "': " + interfaceName));
		}
		else if (!POJOUtils.isImplementation(service, iface)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Configured service '" + service.getId() + "' for '" + fieldName + "' does not implement " + interfaceName));
		}
	}

	private void validatePoolProxy(JDBCPoolConfiguration configuration, List<Validation<?>> validations) {
		if (configuration.getPoolProxy() == null) {
			return;
		}
		if (!(configuration.getPoolProxy() instanceof JDBCPoolArtifact)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Configured poolProxy '" + configuration.getPoolProxy().getId() + "' is not a " + JDBCPoolArtifact.class.getName()));
		}
	}

	@SuppressWarnings("unchecked")
	private void validateDriver(String driverClassName, List<Validation<?>> validations) {
		if (driverClassName == null || driverClassName.trim().isEmpty()) {
			return;
		}
		List<String> drivers = new ArrayList<String>();
		for (Class<?> implementation : EAIRepositoryUtils.getImplementationsFor(EAIResourceRepository.getInstance().getClassLoader(), Driver.class)) {
			if (implementation != null) {
				drivers.add(implementation.getName());
			}
		}
		if (!drivers.isEmpty() && !drivers.contains(driverClassName)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Configured driverClassName '" + driverClassName + "' is not currently available in the loaded modules"));
		}
	}

	@SuppressWarnings("unchecked")
	private void validateDialect(Class<?> dialect, List<Validation<?>> validations) {
		if (dialect == null) {
			return;
		}
		if (!SQLDialect.class.isAssignableFrom(dialect)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Configured dialect '" + dialect.getName() + "' does not implement " + SQLDialect.class.getName()));
			return;
		}
		List<Class<SQLDialect>> implementations = (List<Class<SQLDialect>>) (List<?>) EAIRepositoryUtils.getImplementationsFor(EAIResourceRepository.getInstance().getClassLoader(), SQLDialect.class);
		if (implementations != null && !implementations.isEmpty() && !implementations.contains(dialect)) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "Configured dialect '" + dialect.getName() + "' is not currently available in the loaded modules"));
		}
	}

	private void validatePoolSizing(JDBCPoolConfiguration configuration, List<Validation<?>> validations) {
		if (configuration.getMinimumIdle() != null && configuration.getMinimumIdle() < 0) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "minimumIdle can not be negative"));
		}
		if (configuration.getMaximumPoolSize() != null && configuration.getMaximumPoolSize() <= 0) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "maximumPoolSize must be greater than zero"));
		}
		if (configuration.getMinimumIdle() != null && configuration.getMaximumPoolSize() != null && configuration.getMinimumIdle() > configuration.getMaximumPoolSize()) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "minimumIdle can not be greater than maximumPoolSize"));
		}
		if (configuration.getConnectionTimeout() != null && configuration.getConnectionTimeout() < 0) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "connectionTimeout can not be negative"));
		}
		if (configuration.getIdleTimeout() != null && configuration.getIdleTimeout() < 0) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "idleTimeout can not be negative"));
		}
		if (configuration.getMaxLifetime() != null && configuration.getMaxLifetime() < 0) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, "maxLifetime can not be negative"));
		}
	}

	private String loadGuidelinesResource(String resourcePath) {
		return EAIRepositoryUtils.loadCachedClasspathResource(JDBCPoolArtifactFragmentManager.class, resourcePath);
	}

	@SuppressWarnings("unchecked")
	private String buildDynamicDriverGuidelines() {
		List<String> names = new ArrayList<String>();
		for (Class<?> implementation : EAIRepositoryUtils.getImplementationsFor(EAIResourceRepository.getInstance().getClassLoader(), Driver.class)) {
			if (implementation != null) {
				names.add(implementation.getName());
			}
		}
		if (names.isEmpty()) {
			return "Known drivers right now:\n- none discovered in the current module set";
		}
		Collections.sort(names);
		return "Known drivers right now:\n- " + String.join("\n- ", names);
	}

	@SuppressWarnings("unchecked")
	private String buildDynamicDialectGuidelines() {
		List<Class<SQLDialect>> implementations = (List<Class<SQLDialect>>) (List<?>) EAIRepositoryUtils.getImplementationsFor(EAIResourceRepository.getInstance().getClassLoader(), SQLDialect.class);
		if (implementations == null || implementations.isEmpty()) {
			return "Known dialects right now:\n- none discovered in the current module set";
		}
		List<String> names = new ArrayList<String>();
		for (Class<SQLDialect> implementation : implementations) {
			if (implementation != null) {
				names.add(implementation.getName());
			}
		}
		Collections.sort(names);
		return "Known dialects right now:\n- " + String.join("\n- ", names);
	}

	private boolean hasErrors(List<Validation<?>> validations) {
		for (Validation<?> validation : validations) {
			if (validation != null && validation.getSeverity() == ValidationMessage.Severity.ERROR) {
				return true;
			}
		}
		return false;
	}

	private Document parseDocument(String content) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
	}

	private Element getDirectChild(Element parent, String name) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child instanceof Element) {
				Element element = (Element) child;
				String childName = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
				if (name.equals(childName)) {
					return element;
				}
			}
		}
		return null;
	}

	private void retainField(Element currentRoot, Element updatedRoot, Document updated, String name) {
		if (getDirectChild(updatedRoot, name) == null) {
			Element existing = getDirectChild(currentRoot, name);
			if (existing != null) {
				updatedRoot.appendChild(updated.importNode(existing, true));
			}
		}
	}

	private void removeDirectChild(Element parent, String name) {
		Element child = getDirectChild(parent, name);
		while (child != null) {
			parent.removeChild(child);
			child = getDirectChild(parent, name);
		}
	}

	private String toXml(Document document) throws Exception {
		javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		transformer.transform(new javax.xml.transform.dom.DOMSource(document), new javax.xml.transform.stream.StreamResult(output));
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}
}
