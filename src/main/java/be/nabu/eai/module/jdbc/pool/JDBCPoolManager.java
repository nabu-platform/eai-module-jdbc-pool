package be.nabu.eai.module.jdbc.pool;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class JDBCPoolManager extends JAXBArtifactManager<JDBCPoolConfiguration, JDBCPoolArtifact> {

	public JDBCPoolManager() {
		super(JDBCPoolArtifact.class);
	}

	@Override
	protected JDBCPoolArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new JDBCPoolArtifact(id, container, repository);
	}

}
