package be.nabu.eai.module.jdbc.context;

import java.util.LinkedHashSet;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.managers.base.BaseConfigurationGUIManager;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.Confirm;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.developer.util.Confirm.ConfirmType;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.Entry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.ComplexElementImpl;

public class GenerateDatabaseScriptContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		if (entry.isNode() && ComplexType.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
			Menu menu = new Menu("SQL");
			Menu create = new Menu("Create DDL");
			Menu insert = new Menu("Insert DML");
			for (final Class<SQLDialect> clazz : EAIRepositoryUtils.getImplementationsFor(entry.getRepository().getClassLoader(), SQLDialect.class)) {
				MenuItem createItem = new MenuItem(clazz.getSimpleName());
				createItem.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						try {
							Confirm.confirm(ConfirmType.INFORMATION, "Create SQL: " + entry.getId(), clazz.newInstance().buildCreateSQL((ComplexType) entry.getNode().getArtifact()), null);
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
				create.getItems().add(createItem);
				
				MenuItem insertItem = new MenuItem(clazz.getSimpleName());
				insertItem.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@SuppressWarnings({ "unchecked", "rawtypes" })
					@Override
					public void handle(ActionEvent arg0) {
						try {
							List<Property<?>> supported = BaseConfigurationGUIManager.createProperty(new ComplexElementImpl((ComplexType) entry.getNode().getArtifact(), null));
							SimplePropertyUpdater updater = new SimplePropertyUpdater(true, new LinkedHashSet(supported));
							EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Insert Values", new EventHandler<ActionEvent>() {
								@Override
								public void handle(ActionEvent arg0) {
									try {
										ComplexContent newInstance = ((ComplexType) entry.getNode().getArtifact()).newInstance();
										for (Element<?> element : TypeUtils.getAllChildren(newInstance.getType())) {
											Object value = updater.getValue(element.getName());
											if (value != null) {
												newInstance.set(element.getName(), value);
											}
										}
										Confirm.confirm(ConfirmType.INFORMATION, "Insert SQL: " + entry.getId(), clazz.newInstance().buildInsertSQL(newInstance), null);
									}
									catch (Exception e) {
										e.printStackTrace();
									}
								}
							});
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
				insert.getItems().add(insertItem);
			}
			menu.getItems().addAll(create, insert);
			return menu;
		}
		else if (!entry.isLeaf()) {
			boolean hasComplexType = false;
			for (Entry child : entry) {
				if (child.isNode() && ComplexType.class.isAssignableFrom(child.getNode().getArtifactClass())) {
					hasComplexType = true;
					break;
				}
			}
			if (hasComplexType) {
				Menu menu = new Menu("Create All SQL");
				for (final Class<SQLDialect> clazz : EAIRepositoryUtils.getImplementationsFor(entry.getRepository().getClassLoader(), SQLDialect.class)) {
					MenuItem item = new MenuItem(clazz.getSimpleName());
					item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							try {
								StringBuilder builder = new StringBuilder();
								SQLDialect dialect = clazz.newInstance();
								for (Entry child : entry) {
									if (child.isNode() && ComplexType.class.isAssignableFrom(child.getNode().getArtifactClass())) {
										builder.append(dialect.buildCreateSQL((ComplexType) child.getNode().getArtifact()));
										builder.append("\n");
									}
								}
								Confirm.confirm(ConfirmType.INFORMATION, "Create All SQL: " + entry.getId(), builder.toString(), null);
							}
							catch (Exception e) {
								e.printStackTrace();
							}
						}
					});
					menu.getItems().add(item);
				}
				return menu;
			}
		}
		return null;
	}

}
