package be.nabu.eai.module.jdbc.pool;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class JDBCPoolGUIManager extends BaseJAXBGUIManager<JDBCPoolConfiguration, JDBCPoolArtifact> {

	public JDBCPoolGUIManager() {
		super("JDBC Pool", JDBCPoolArtifact.class, new JDBCPoolManager(), JDBCPoolConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected JDBCPoolArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new JDBCPoolArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
	}

	@Override
	public String getCategory() {
		return "Protocols";
	}
}
