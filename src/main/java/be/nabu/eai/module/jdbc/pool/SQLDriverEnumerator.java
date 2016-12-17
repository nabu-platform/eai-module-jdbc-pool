package be.nabu.eai.module.jdbc.pool;

import java.sql.Driver;
import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.api.Enumerator;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;

public class SQLDriverEnumerator implements Enumerator {

	@Override
	public List<?> enumerate() {
		List<String> values = new ArrayList<String>();
		for (Class<?> implementation : EAIRepositoryUtils.getImplementationsFor(EAIResourceRepository.getInstance().getClassLoader(), Driver.class)) {
			values.add(implementation.getName());
		}
		return values;
	}

}
