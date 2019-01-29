package be.nabu.eai.module.jdbc.pool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import be.nabu.eai.developer.api.InterfaceLister;
import be.nabu.eai.developer.util.InterfaceDescriptionImpl;

public class JDBCPoolInterfaceLister implements InterfaceLister {

	private static Collection<InterfaceDescription> descriptions = null;
	
	@Override
	public Collection<InterfaceDescription> getInterfaces() {
		if (descriptions == null) {
			synchronized(JDBCPoolInterfaceLister.class) {
				if (descriptions == null) {
					List<InterfaceDescription> descriptions = new ArrayList<InterfaceDescription>();
					descriptions.add(new InterfaceDescriptionImpl("JDBC Pool", "Translation Getter", "be.nabu.libs.services.jdbc.api.JDBCTranslator.get"));
					descriptions.add(new InterfaceDescriptionImpl("JDBC Pool", "Translation Setter", "be.nabu.libs.services.jdbc.api.JDBCTranslator.set"));
					JDBCPoolInterfaceLister.descriptions = descriptions;
				}
			}
		}
		return descriptions;
	}

}
