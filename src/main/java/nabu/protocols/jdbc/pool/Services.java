package nabu.protocols.jdbc.pool;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

import nabu.protocols.jdbc.pool.types.JDBCPoolInformation;
import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.repository.EAIResourceRepository;

@WebService
public class Services {
	
	@WebResult(name = "information")
	public JDBCPoolInformation information(@WebParam(name = "jdbcPoolId") String jdbcPoolId) {
		if (jdbcPoolId != null) {
			JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
			if (resolve != null) {
				JDBCPoolInformation information = new JDBCPoolInformation();
				information.setDefaultLanguage(resolve.getConfig().getDefaultLanguage());
				information.setTranslatable(resolve.getConfig().getTranslationGet() != null && resolve.getConfig().getTranslationSet() != null);
				if (resolve.getConfig().getDialect() != null) {
					information.setDialect(resolve.getConfig().getDialect().getName());
				}
				information.setDriverClass(resolve.getConfig().getDriverClassName());
				information.setJdbcUrl(resolve.getConfig().getJdbcUrl());
				information.setUsername(resolve.getConfig().getUsername());
				return information;
			}
		}
		return null;
	}
}
