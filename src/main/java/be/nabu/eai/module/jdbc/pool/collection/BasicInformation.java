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

package be.nabu.eai.module.jdbc.pool.collection;

import be.nabu.libs.types.api.annotation.ComplexTypeDescriptor;
import be.nabu.libs.types.api.annotation.Field;

@ComplexTypeDescriptor(propOrder = { "name", "mainDatabase", "hideMainOption", "correctName" })
public class BasicInformation {
	private boolean mainDatabase, hideMainOption;
	private String name, correctName;
	
	@Field(comment = "The main database for the application will contain all application-specific data like users, schedulers...", hide = "hideMainOption")
	public boolean isMainDatabase() {
		return mainDatabase;
	}
	public void setMainDatabase(boolean mainDatabase) {
		this.mainDatabase = mainDatabase;
	}
	@Field(comment = "The name of the database.", hide = "mainDatabase")
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	@Field(hide = "true")
	public boolean isHideMainOption() {
		return hideMainOption;
	}
	public void setHideMainOption(boolean hideMainOption) {
		this.hideMainOption = hideMainOption;
	}
	@Field(hide = "true")
	public String getCorrectName() {
		return correctName;
	}
	public void setCorrectName(String correctName) {
		this.correctName = correctName;
	}
	
}
