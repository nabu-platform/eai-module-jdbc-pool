package be.nabu.eai.module.jdbc.pool;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.api.Enumerator;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.libs.services.jdbc.api.SQLDialect;

public class SQLDialectEnumerator implements Enumerator {

	@Override
	public List<?> enumerate() {
		List<Class<?>> values = new ArrayList<Class<?>>();
		for (Class<?> implementation : EAIRepositoryUtils.getImplementationsFor(EAIResourceRepository.getInstance().getClassLoader(), SQLDialect.class)) {
			values.add(implementation);
		}
		return values;
	}

}
