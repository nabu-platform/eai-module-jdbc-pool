package be.nabu.eai.module.jdbc.pool.api;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.validator.api.ValidationMessage;

public interface JDBCPoolWizard<T> {
	public String getIcon();
	public String getName();
	
	// additional description if relevant
	public default String getDescription() {
		return null;
	}
	
	// the class that will be used
	public Class<T> getWizardClass();
	
	// return an instance of the properties for an existing pool
	public T load(JDBCPoolArtifact pool);
	
	// apply to an existing, for new ones the existing will be null!
	public JDBCPoolArtifact apply(Entry project, RepositoryEntry entry, T properties, boolean isNew, boolean isMain);
	
	public default List<ValidationMessage> validate(T content) {
		return new ArrayList<ValidationMessage>();
	}
}
