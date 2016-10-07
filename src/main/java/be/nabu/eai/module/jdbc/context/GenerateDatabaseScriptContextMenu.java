package be.nabu.eai.module.jdbc.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.types.properties.ForeignKeyProperty;

public class GenerateDatabaseScriptContextMenu implements EntryContextMenuProvider {

	private static final class ForeignKeyComparator implements Comparator<ComplexType> {
		
		private boolean reverse;

		public ForeignKeyComparator(boolean reverse) {
			this.reverse = reverse;
		}
		
		@Override
		public int compare(ComplexType o1, ComplexType o2) {
			int multiplier = reverse ? -1 : 1;
			boolean hasForeign1 = false;
			for (Element<?> element : TypeUtils.getAllChildren(o1)) {
				Value<String> foreign = element.getProperty(ForeignKeyProperty.getInstance());
				hasForeign1 |= (foreign != null && !foreign.getValue().equals(((DefinedType) o1).getId()));
				if (foreign != null && foreign.getValue().split(":")[0].equals(((DefinedType) o2).getId())) {
					return 1 * multiplier;
				}
			}
			boolean hasForeign2 = false;
			for (Element<?> element : TypeUtils.getAllChildren(o2)) {
				Value<String> foreign = element.getProperty(ForeignKeyProperty.getInstance());
				hasForeign2 |= (foreign != null && !foreign.getValue().equals(((DefinedType) o2).getId()));
				if (foreign != null && foreign.getValue().split(":")[0].equals(((DefinedType) o1).getId())) {
					return -1 * multiplier;
				}
			}
			if (!hasForeign1 && hasForeign2) {
				return -1 * multiplier;
			}
			else if (hasForeign1 && !hasForeign2) {
				return 1 * multiplier;
			}
			else {
				return 0;
			}
		}
	}
	
	// this does not pick up circular dependencies
	private static <T> void deepSort(List<T> objects, Comparator<T> comparator) {
		// do an initial sort
		Collections.sort(objects, comparator);
		boolean changed = true;
		changing: while(changed) {
			changed = false;
			for (int i = 0; i < objects.size() - 1; i++) {
				for (int j = i + 1; j < objects.size(); j++) {
					int compare = comparator.compare(objects.get(i), objects.get(j));
					if (compare > 0) {
						T tmp = objects.get(j);
						objects.set(j, objects.get(i));
						objects.set(i, tmp);
						changed = true;
						continue changing;
					}
				}
			}
		}
	}

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
				Menu menu = new Menu("SQL");
				
				Menu create = new Menu("Create DDL");
				for (final Class<SQLDialect> clazz : EAIRepositoryUtils.getImplementationsFor(entry.getRepository().getClassLoader(), SQLDialect.class)) {
					MenuItem item = new MenuItem(clazz.getSimpleName());
					item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							try {
								List<ComplexType> typesToDrop = new ArrayList<ComplexType>();
								for (Entry child : entry) {
									if (child.isNode() && ComplexType.class.isAssignableFrom(child.getNode().getArtifactClass())) {
										typesToDrop.add((ComplexType) child.getNode().getArtifact());
									}
								}
								deepSort(typesToDrop, new ForeignKeyComparator(false));
								
								StringBuilder builder = new StringBuilder();
								SQLDialect dialect = clazz.newInstance();
								for (ComplexType type : typesToDrop) {
									builder.append(dialect.buildCreateSQL(type));
									builder.append("\n");
								}
								Confirm.confirm(ConfirmType.INFORMATION, "Create All SQL: " + entry.getId(), builder.toString(), null);
							}
							catch (Exception e) {
								e.printStackTrace();
							}
						}
					});
					create.getItems().addAll(item);
				}
				
				MenuItem dropItem = new MenuItem("Drop DDL");
				dropItem.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						try {
							List<ComplexType> typesToDrop = new ArrayList<ComplexType>();
							for (Entry child : entry) {
								if (child.isNode() && ComplexType.class.isAssignableFrom(child.getNode().getArtifactClass())) {
									typesToDrop.add((ComplexType) child.getNode().getArtifact());
								}
							}
							deepSort(typesToDrop, new ForeignKeyComparator(true));
							StringBuilder builder = new StringBuilder();
							for (ComplexType type : typesToDrop) {
								String value = ValueUtils.getValue(CollectionNameProperty.getInstance(), type.getProperties());
								if (value == null) {
									value = type.getName();
								}
								builder.append("drop table ")
									.append(EAIRepositoryUtils.uncamelify(value))
									.append(";\n");
							}
							Confirm.confirm(ConfirmType.INFORMATION, "Drop Table SQL: " + entry.getId(), builder.toString(), null);
						}
						catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
				});
				
				menu.getItems().addAll(create, dropItem);
				return menu;
			}
		}
		return null;
	}

}
