package be.nabu.eai.module.jdbc.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import nabu.protocols.jdbc.pool.types.TableDescription;
import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.managers.base.BaseConfigurationGUIManager;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.Confirm;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.module.jdbc.pool.JDBCPoolUtils;
import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.developer.util.Confirm.ConfirmType;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;

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
			Menu delete = new Menu("Delete DML");
			delete.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					try {
						ComplexType type = (ComplexType) entry.getNode().getArtifact();
						// we need to make sure that everything with a foreign key to this element is deleted first
						StringBuilder builder = new StringBuilder();
						// TODO: generate a script (with or without id?) that you can use in the correct order to delete something
						// if without id, might as well drop...?
					}
					catch (Exception e) {
						MainController.getInstance().notify(e);
					}
				}
			});
			
			// add a synchronize option
			Menu synchronize = new Menu("Synchronize");
			for (JDBCPoolArtifact artifact : entry.getRepository().getArtifacts(JDBCPoolArtifact.class)) {
				MenuItem item = new MenuItem(artifact.getId());
				item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {
						try {
							if (artifact.getConfig().getManagedTypes() == null) {
								artifact.getConfig().setManagedTypes(new ArrayList<String>());
							}
							if (!artifact.getConfig().getManagedTypes().contains(entry.getNode().getArtifact().getId())) {
								artifact.getConfig().getManagedTypes().add(entry.getNode().getArtifact().getId());
								artifact.save(artifact.getDirectory());
								MainController.getInstance().getServer().getRemote().reload(artifact.getId());
								MainController.getInstance().getCollaborationClient().updated(artifact.getId(), "Added managed types");
							}
							synchronizeManagedTypes(artifact);
						}
						catch (Exception e) {
							MainController.getInstance().notify(e);
						}
					}
				});
				synchronize.getItems().add(item);
				synchronize.getItems().sort(new Comparator<MenuItem>() {
					@Override
					public int compare(MenuItem o1, MenuItem o2) {
						return o1.getText().compareTo(o2.getText());
					}
				});
			}
			menu.getItems().addAll(create, insert, synchronize);
			
			return menu;
		}
		else if (entry.isNode() && JDBCPoolArtifact.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
			try {
				Menu menu = new Menu("Synchronize");
				MenuItem toDatabase = new MenuItem("To Database");
				JDBCPoolArtifact artifact = (JDBCPoolArtifact) entry.getNode().getArtifact();
				toDatabase.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {
						try {
							synchronizeManagedTypes(artifact);
						}
						catch (Exception e) {
							MainController.getInstance().notify(e);
						}
					}
				});
				menu.getItems().addAll(toDatabase);
				if (artifact.getConfig().getManagedTypes() != null && !artifact.getConfig().getManagedTypes().isEmpty()) {
					MenuItem fromDatabase = new MenuItem("From Database");
					fromDatabase.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent event) {
							try {
								synchronizeManagedTypesFromDatabase(artifact, artifact.getConfig().getManagedTypes());
							}
							catch (Exception e) {
								MainController.getInstance().notify(e);
							}
						}
					});
					menu.getItems().addAll(fromDatabase);
				}
				return menu;
			}
			catch (Exception e) {
				MainController.getInstance().notify(e);
			}
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
								JDBCPoolUtils.deepSort(typesToDrop, new JDBCPoolUtils.ForeignKeyComparator(false));
								
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
							JDBCPoolUtils.deepSort(typesToDrop, new JDBCPoolUtils.ForeignKeyComparator(true));
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
				
				// add a synchronize option
				Menu synchronize = new Menu("Synchronize");
				for (JDBCPoolArtifact artifact : entry.getRepository().getArtifacts(JDBCPoolArtifact.class)) {
					MenuItem item = new MenuItem(artifact.getId());
					item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent event) {
							try {
								if (artifact.getConfig().getManagedTypes() == null) {
									artifact.getConfig().setManagedTypes(new ArrayList<String>());
								}
								List<ComplexType> typesToSynchronize = new ArrayList<ComplexType>();
								for (Entry child : entry) {
									if (child.isNode() && ComplexType.class.isAssignableFrom(child.getNode().getArtifactClass())) {
										typesToSynchronize.add((ComplexType) child.getNode().getArtifact());
									}
								}
								boolean changed = false;
								for (ComplexType type : typesToSynchronize) {
									if (!artifact.getConfig().getManagedTypes().contains(((DefinedType) type).getId())) {
										artifact.getConfig().getManagedTypes().add(((DefinedType) type).getId());
										changed = true;
									}
								}
								if (changed) {
									artifact.save(artifact.getDirectory());
									MainController.getInstance().getServer().getRemote().reload(artifact.getId());
									MainController.getInstance().getCollaborationClient().updated(artifact.getId(), "Added managed types");
								}
								synchronizeManagedTypes(artifact);
							}
							catch (Exception e) {
								MainController.getInstance().notify(e);
							}
						}
					});
					synchronize.getItems().add(item);
					synchronize.getItems().sort(new Comparator<MenuItem>() {
						@Override
						public int compare(MenuItem o1, MenuItem o2) {
							return o1.getText().compareTo(o2.getText());
						}
					});
				}
				
				menu.getItems().addAll(create, dropItem, synchronize);
				return menu;
			}
		}
		return null;
	}

	private void synchronizeManagedTypes(JDBCPoolArtifact artifact) throws InterruptedException, ExecutionException {
		Service service = (Service) EAIResourceRepository.getInstance().resolve("nabu.protocols.jdbc.pool.Services.synchronizeManagedTypes");
		if (service != null) {
			ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
			input.set("jdbcPoolId", artifact.getId());
			input.set("force", true);
			Future<ServiceResult> run = EAIResourceRepository.getInstance().getServiceRunner().run(service, EAIResourceRepository.getInstance().newExecutionContext(SystemPrincipal.ROOT), input);
			ServiceResult serviceResult = run.get();
			if (serviceResult.getException() != null) {
				MainController.getInstance().notify(serviceResult.getException());
			}
		}
	}
	
	private void synchronizeManagedTypesFromDatabase(JDBCPoolArtifact pool, List<String> managedTypes) {
		Service service = (Service) EAIResourceRepository.getInstance().resolve("nabu.protocols.jdbc.pool.Services.listTables");
		if (service != null) {
			for (String managedType : managedTypes) {
				try {
					Artifact resolve = MainController.getInstance().getRepository().resolve(managedType);
					if (resolve instanceof DefinedStructure) {
						String collectionName = ValueUtils.getValue(CollectionNameProperty.getInstance(), ((Structure) resolve).getProperties());
						if (collectionName != null) {
							ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
							input.set("jdbcPoolId", pool.getId());
							collectionName = NamingConvention.UNDERSCORE.apply(collectionName);
							input.set("tablePattern", collectionName);
							input.set("limitToCurrentSchema", true);
							Future<ServiceResult> run = EAIResourceRepository.getInstance().getServiceRunner().run(service, EAIResourceRepository.getInstance().newExecutionContext(SystemPrincipal.ROOT), input);
							ServiceResult serviceResult = run.get();
							if (serviceResult.getException() != null) {
								MainController.getInstance().notify(serviceResult.getException());
							}
							else {
								List<Object> objects = (List<Object>) serviceResult.getOutput().get("tables");
								// could be multiple tables if you have for example "node" and "node_other"
								for (Object object : objects) {
									TableDescription description = object instanceof TableDescription ? (TableDescription) object : TypeUtils.getAsBean((ComplexContent) object, TableDescription.class);
									if (description.getName().equalsIgnoreCase(collectionName)) {
										JDBCPoolUtils.toType((Structure) resolve, description);
										new StructureManager().save((ResourceEntry) MainController.getInstance().getRepository().getEntry(resolve.getId()), (DefinedStructure) resolve);
										MainController.getInstance().getRepository().reload(resolve.getId());
										MainController.getInstance().getServer().getRemote().reload(resolve.getId());
										MainController.getInstance().getCollaborationClient().updated(resolve.getId(), "Refreshed managed types");
									}
								}
							}
						}
					}
				}
				catch (Exception e) {
					MainController.getInstance().notify(e);
				}
			}
		}
	}
}
