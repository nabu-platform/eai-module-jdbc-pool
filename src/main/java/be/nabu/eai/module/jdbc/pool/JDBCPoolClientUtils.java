package be.nabu.eai.module.jdbc.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import nabu.protocols.jdbc.pool.types.TableDescription;

public class JDBCPoolClientUtils {
	
	@SuppressWarnings("unchecked")
	public static List<TableDescription> listTables(JDBCPoolArtifact pool) {
		List<TableDescription> tables = new ArrayList<TableDescription>();
		try {
			Service service = (Service) EAIResourceRepository.getInstance().resolve("nabu.protocols.jdbc.pool.Services.listTables");
			if (service != null) {
				ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
				input.set("jdbcPoolId", pool.getId());
				input.set("limitToCurrentSchema", true);
				Future<ServiceResult> run = EAIResourceRepository.getInstance().getServiceRunner().run(service, EAIResourceRepository.getInstance().newExecutionContext(SystemPrincipal.ROOT), input);
				ServiceResult serviceResult = run.get();
				if (serviceResult.getException() != null) {
					MainController.getInstance().notify(serviceResult.getException());
				}
				else {
					List<Object> objects = (List<Object>) serviceResult.getOutput().get("tables");
					if (objects != null) {
						// could be multiple tables if you have for example "node" and "node_other"
						for (Object object : objects) {
							TableDescription description = object instanceof TableDescription ? (TableDescription) object : TypeUtils.getAsBean((ComplexContent) object, TableDescription.class);
							tables.add(description);
						}
					}
				}
			}
		}
		catch (Exception e) {
			MainController.getInstance().notify(e);
		}
		return tables;
	}
	
}
