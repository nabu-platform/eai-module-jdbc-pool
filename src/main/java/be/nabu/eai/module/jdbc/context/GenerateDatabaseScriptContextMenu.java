package be.nabu.eai.module.jdbc.context;

import java.util.ArrayList;
import java.util.Arrays;
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
import be.nabu.eai.module.jdbc.pool.JDBCPoolManager;
import be.nabu.eai.module.jdbc.pool.JDBCPoolUtils;
import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.developer.util.Confirm.ConfirmType;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.util.SystemPrincipal;
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
			Menu fromDatabase = new Menu("From database");
			Menu toDatabase = new Menu("To database");
			synchronize.getItems().addAll(toDatabase, fromDatabase);
			for (JDBCPoolArtifact artifact : entry.getRepository().getArtifacts(JDBCPoolArtifact.class)) {
				if (canEdit(artifact.getId())) {
					MenuItem toItem = new MenuItem(artifact.getId());
					toItem.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent event) {
							try {
								if (artifact.getConfig().getManagedTypes() == null) {
									artifact.getConfig().setManagedTypes(new ArrayList<DefinedType>());
								}
								if (!artifact.getConfig().getManagedTypes().contains(entry.getNode().getArtifact())) {
									artifact.getConfig().getManagedTypes().add((DefinedType) entry.getNode().getArtifact());
									new JDBCPoolManager().save((ResourceEntry) entry.getRepository().getEntry(artifact.getId()), artifact);
									MainController.getInstance().getRepository().reload(artifact.getId());
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
					toDatabase.getItems().add(toItem);
					try {
						if (artifact.getConfig().getManagedTypes() != null && artifact.getConfig().getManagedTypes().contains(entry.getNode().getArtifact())) {
							MenuItem fromItem = new MenuItem(artifact.getId());
							fromItem.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
								@Override
								public void handle(ActionEvent arg0) {
									try {
										synchronizeManagedTypesFromDatabase(artifact, Arrays.asList((DefinedType) entry.getNode().getArtifact()));
									}
									catch (Exception e) {
										MainController.getInstance().notify(e);
									}
								}
							});
							fromDatabase.getItems().add(fromItem);
						}
					}
					catch (Exception e) {
						MainController.getInstance().notify(e);
					}
				}
			}
			toDatabase.getItems().sort(new Comparator<MenuItem>() {
				@Override
				public int compare(MenuItem o1, MenuItem o2) {
					return o1.getText().compareTo(o2.getText());
				}
			});
			if (fromDatabase.getItems().isEmpty()) {
				synchronize.getItems().remove(fromDatabase);
			}
			else {
				fromDatabase.getItems().sort(new Comparator<MenuItem>() {
					@Override
					public int compare(MenuItem o1, MenuItem o2) {
						return o1.getText().compareTo(o2.getText());
					}
				});
			}
			menu.getItems().addAll(create, insert);
			// the to contains all the connections, the from is a subselection
			if (!toDatabase.getItems().isEmpty()) {
				menu.getItems().add(synchronize);
			}
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
								relinkAll(artifact);
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
								List<ComplexType> uncollectedTypes = new ArrayList<ComplexType>();
								for (Entry child : entry) {
									if (child.isNode() && ComplexType.class.isAssignableFrom(child.getNode().getArtifactClass())) {
										ComplexType complexTypeToAdd = (ComplexType) child.getNode().getArtifact();
										// only automatically synchronize types that have a collection name!
										// the others are likely not of interest
										if (ValueUtils.getValue(CollectionNameProperty.getInstance(), complexTypeToAdd.getProperties()) != null) {
											typesToDrop.add(complexTypeToAdd);
										}
										else {
											uncollectedTypes.add(complexTypeToAdd);
										}
									}
								}
								// again: presumably not using collection names...
								if (typesToDrop.isEmpty()) {
									typesToDrop.addAll(uncollectedTypes);
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
							List<ComplexType> uncollectedTypes = new ArrayList<ComplexType>();
							for (Entry child : entry) {
								if (child.isNode() && ComplexType.class.isAssignableFrom(child.getNode().getArtifactClass())) {
									ComplexType complexTypeToAdd = (ComplexType) child.getNode().getArtifact();
									// only automatically synchronize types that have a collection name!
									// the others are likely not of interest
									if (ValueUtils.getValue(CollectionNameProperty.getInstance(), complexTypeToAdd.getProperties()) != null) {
										typesToDrop.add(complexTypeToAdd);
									}
									else {
										uncollectedTypes.add(complexTypeToAdd);
									}
								}
							}
							// again: presumably not using collection names...
							if (typesToDrop.isEmpty()) {
								typesToDrop.addAll(uncollectedTypes);
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
				Menu toDatabase = new Menu("To database");
				Menu fromDatabase = new Menu("From database");
				for (JDBCPoolArtifact artifact : entry.getRepository().getArtifacts(JDBCPoolArtifact.class)) {
					if (canEdit(artifact.getId())) {
						MenuItem toItem = new MenuItem(artifact.getId());
						toItem.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent event) {
								try {
									if (artifact.getConfig().getManagedTypes() == null) {
										artifact.getConfig().setManagedTypes(new ArrayList<DefinedType>());
									}
									List<ComplexType> typesToSynchronize = new ArrayList<ComplexType>();
									List<ComplexType> uncollectedTypes = new ArrayList<ComplexType>();
									for (Entry child : entry) {
										if (child.isNode() && ComplexType.class.isAssignableFrom(child.getNode().getArtifactClass())) {
											ComplexType complexTypeToSynchronize = (ComplexType) child.getNode().getArtifact();
											// only automatically synchronize types that have a collection name!
											// the others are likely not of interest
											if (ValueUtils.getValue(CollectionNameProperty.getInstance(), complexTypeToSynchronize.getProperties()) != null) {
												typesToSynchronize.add(complexTypeToSynchronize);
											}
											else {
												uncollectedTypes.add(complexTypeToSynchronize);
											}
										}
									}
									// if we have no correctly annotated types, we presumably are not using collection names as a thing, just add all the types...
									if (typesToSynchronize.isEmpty()) {
										typesToSynchronize.addAll(uncollectedTypes);
									}
									boolean changed = false;
									for (ComplexType type : typesToSynchronize) {
										if (!artifact.getConfig().getManagedTypes().contains((DefinedType) type)) {
											artifact.getConfig().getManagedTypes().add((DefinedType) type);
											changed = true;
										}
									}
									if (changed) {
										new JDBCPoolManager().save((ResourceEntry) entry.getRepository().getEntry(artifact.getId()), artifact);
										MainController.getInstance().getRepository().reload(artifact.getId());
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
						toDatabase.getItems().add(toItem);
						
						if (artifact.getConfig().getManagedTypes() != null) {
							// check if we have any here
							List<DefinedType> typesToSynchronize = new ArrayList<DefinedType>();
							for (Entry child : entry) {
								try {
									if (child.isNode() && ComplexType.class.isAssignableFrom(child.getNode().getArtifactClass()) && artifact.getConfig().getManagedTypes().contains(child.getNode().getArtifact())) {
										typesToSynchronize.add((DefinedType) child.getNode().getArtifact());
									}
								}
								catch (Exception e) {
									MainController.getInstance().notify(e);
								}
							}
							// if we have types we can resynchronize, allow the option
							if (!typesToSynchronize.isEmpty()) {
								MenuItem fromItem = new MenuItem(artifact.getId());
								fromItem.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
									@Override
									public void handle(ActionEvent arg0) {
										synchronizeManagedTypesFromDatabase(artifact, typesToSynchronize);
									}
								});
								fromDatabase.getItems().add(fromItem);
							}
						}
					}
				}
				toDatabase.getItems().sort(new Comparator<MenuItem>() {
					@Override
					public int compare(MenuItem o1, MenuItem o2) {
						return o1.getText().compareTo(o2.getText());
					}
				});
				fromDatabase.getItems().sort(new Comparator<MenuItem>() {
					@Override
					public int compare(MenuItem o1, MenuItem o2) {
						return o1.getText().compareTo(o2.getText());
					}
				});
				
				synchronize.getItems().add(toDatabase);
				if (!fromDatabase.getItems().isEmpty()) {
					synchronize.getItems().add(fromDatabase);
				}
				
				menu.getItems().addAll(create, dropItem);
				
				if (!toDatabase.getItems().isEmpty()) {
					menu.getItems().add(synchronize);
				}
				return menu;
			}
		}
		return null;
	}

	public static void synchronizeManagedTypes(JDBCPoolArtifact artifact) throws InterruptedException, ExecutionException {
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
	
	@SuppressWarnings("unchecked")
	private void relinkAll(JDBCPoolArtifact pool) {
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
						List<TableDescription> tables = new ArrayList<TableDescription>();
						// could be multiple tables if you have for example "node" and "node_other"
						for (Object object : objects) {
							TableDescription description = object instanceof TableDescription ? (TableDescription) object : TypeUtils.getAsBean((ComplexContent) object, TableDescription.class);
							tables.add(description);
						}
						JDBCPoolUtils.relink(pool, tables);
					}
				}
			}
		}
		catch (Exception e) {
			MainController.getInstance().notify(e);
		}
	}
	
	public static boolean canEdit(String id) {
		return MainController.getInstance().getRepository().getEntry(id) instanceof ResourceEntry;
	}
	
	@SuppressWarnings("unchecked")
	private void synchronizeManagedTypesFromDatabase(JDBCPoolArtifact pool, List<DefinedType> managedTypes) {
		Service service = (Service) EAIResourceRepository.getInstance().resolve("nabu.protocols.jdbc.pool.Services.listTables");
		if (service != null) {
			for (DefinedType managedType : managedTypes) {
				// no point in trying...
				if (!canEdit(managedType.getId())) {
					continue;
				}
				try {
					if (managedType instanceof DefinedStructure) {
						String collectionName = ValueUtils.getValue(CollectionNameProperty.getInstance(), ((Structure) managedType).getProperties());
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
										if (JDBCPoolUtils.toType((Structure) managedType, description)) {
											new StructureManager().save((ResourceEntry) MainController.getInstance().getRepository().getEntry(managedType.getId()), (DefinedStructure) managedType);
											MainController.getInstance().getRepository().reload(managedType.getId());
											MainController.getInstance().getServer().getRemote().reload(managedType.getId());
											MainController.getInstance().getCollaborationClient().updated(managedType.getId(), "Refreshed managed types");
										}
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
