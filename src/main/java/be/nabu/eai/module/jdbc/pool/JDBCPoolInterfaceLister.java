/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
					descriptions.add(new InterfaceDescriptionImpl("JDBC Pool", "Translation Binding Provider", "be.nabu.libs.services.jdbc.api.JDBCTranslator.getBinding"));
					descriptions.add(new InterfaceDescriptionImpl("JDBC Pool", "Translation Language Mapper", "be.nabu.libs.services.jdbc.api.JDBCTranslator.mapLanguage"));
					JDBCPoolInterfaceLister.descriptions = descriptions;
				}
			}
		}
		return descriptions;
	}

}
