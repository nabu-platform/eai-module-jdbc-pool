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
