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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.developer.CollectionActionImpl;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.CollectionAction;
import be.nabu.eai.developer.api.CollectionManager;
import be.nabu.eai.developer.api.CollectionManagerFactory;
import be.nabu.eai.developer.api.EntryAcceptor;
import be.nabu.eai.developer.collection.EAICollectionUtils;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.module.data.model.DataModelArtifact;
import be.nabu.eai.module.data.model.DataModelManager;
import be.nabu.eai.module.data.model.DataModelType;
import be.nabu.eai.module.jdbc.context.GenerateDatabaseScriptContextMenu;
import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.module.jdbc.pool.JDBCPoolManager;
import be.nabu.eai.module.jdbc.pool.api.JDBCPoolWizard;
import be.nabu.eai.repository.CollectionImpl;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Collection;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeRegistry;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.validator.api.ValidationMessage.Severity;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class JDBCPoolCollectionManagerFactory implements CollectionManagerFactory {

	@Override
	public CollectionManager getCollectionManager(Entry entry) {
		// any folder that contains a JDBC connection is considered a database collection
		for (Entry child : entry) {
			if (child.isNode() && JDBCPoolArtifact.class.isAssignableFrom(child.getNode().getArtifactClass())) {
				return new JDBCPoolCollectionManager(entry);
			}
		}
//		Collection collection = entry.getCollection();
//		if (collection != null && collection.getType().equals("database")) {
//			return new JDBCPoolCollectionManager(entry);
//		}
		return null;
	}

	private static List<JDBCPoolWizard<?>> poolWizards;
	
	@SuppressWarnings("rawtypes")
	public static List<JDBCPoolWizard<?>> getPoolWizards() {
		if (poolWizards == null) {
			synchronized(JDBCPoolCollectionManagerFactory.class) {
				if (poolWizards == null) {
					List<JDBCPoolWizard<?>> poolWizards = new ArrayList<JDBCPoolWizard<?>>();
					for (Class<JDBCPoolWizard> wizard : EAIRepositoryUtils.getImplementationsFor(JDBCPoolWizard.class)) {
						try {
							poolWizards.add(wizard.newInstance());
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					}
					JDBCPoolCollectionManagerFactory.poolWizards = poolWizards;
				}
			}
		}
		return poolWizards;
	}
	
	@Override
	public List<CollectionAction> getActionsFor(Entry entry) {
		List<CollectionAction> actions = new ArrayList<CollectionAction>();
		if (EAICollectionUtils.isProject(entry)) {
			VBox box = new VBox();
			box.getStyleClass().addAll("collection-action", "tile-xsmall");
			Label title = new Label("Add Database");
			title.getStyleClass().add("collection-action-title");
			box.getChildren().addAll(MainController.loadFixedSizeGraphic("database-big.png", 64), title);
			actions.add(new CollectionActionImpl(EAICollectionUtils.newActionTile("database-big.png", "Add Database", "A database is used to persist data."), new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					VBox root = new VBox();
					root.getStyleClass().addAll("collection-actions");
					
					Label title = new Label("Choose your database engine");
					title.getStyleClass().add("h2");
					root.getChildren().add(title);
					root.getStyleClass().add("project");
					Stage stage = EAIDeveloperUtils.buildPopup("Add Database", root, MainController.getInstance().getStage(), StageStyle.DECORATED, false);
					HBox pane = new HBox();
					root.getChildren().add(pane);
					
					boolean hasMain = false;
					// we check if you already have a main application database, in that case we don't show the option to turn it on (there can be only one!)
					for (JDBCPoolArtifact artifact : EAIResourceRepository.getInstance().getArtifacts(JDBCPoolArtifact.class)) {
						if (artifact.getConfig().getContext() != null && Arrays.asList(artifact.getConfig().getContext().split("[\\s]*,[\\s]*")).contains(entry.getId())) {
							hasMain = true;
							break;
						}
					}
					
					List<Node> previous = new ArrayList<Node>();
					final boolean hasMainFinal = hasMain;
					for (JDBCPoolWizard wizard : getPoolWizards()) {
						VBox box = new VBox();
						
						box.getStyleClass().addAll("collection-action", "tile-small");
						box.setAlignment(Pos.CENTER);
						box.setPadding(new Insets(20));
						box.getChildren().add(MainController.loadFixedSizeGraphic(wizard.getIcon(), 64));
						Label label = new Label(wizard.getName());
						label.getStyleClass().add("collection-action-title");
						box.getChildren().add(label);
						Button choose = new Button();
						choose.setGraphic(box);
						pane.getChildren().add(choose);
						choose.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent arg0) {
								try {
									previous.clear();
									previous.addAll(root.getChildren());
									root.getChildren().clear();
									
									VBox form = new VBox();
									root.getChildren().add(form);
									
									// TODO: above the target, always capture the name (for the entry) and a checkbox to indicate whether it is your main application database
									VBox general = new VBox();
									
									Label wizardTitle = new Label("Configure your database");
									wizardTitle.getStyleClass().add("h2");
									form.getChildren().add(wizardTitle);
									
									BasicInformation basicInformation = new BasicInformation();
									basicInformation.setHideMainOption(hasMainFinal);
									
									// if you don't have a main database yet, we assume your first database will be your main
									basicInformation.setMainDatabase(!hasMainFinal);
									SimplePropertyUpdater updater = EAIDeveloperUtils.createUpdater(basicInformation, null);
									MainController.getInstance().showProperties(updater, general, true);
									
									// TODO: need default values (when available) to indicate what would happen if we do nothing?
									
									VBox target = new VBox();
									Object properties = wizard.getWizardClass().newInstance();
									Button create = new Button("Create");
									Button cancel = new Button("Cancel");
									HBox buttons = new HBox();
									create.getStyleClass().add("primary");
									buttons.getStyleClass().add("buttons");
									buttons.getChildren().addAll(create, cancel);
									updater = EAIDeveloperUtils.createUpdater(properties, null);
									MainController.getInstance().showProperties(updater, target, true);
									Separator separator = new Separator(Orientation.HORIZONTAL);
									VBox.setMargin(separator, new Insets(20, 0, 20, 0));
									form.getChildren().addAll(general, separator, target, buttons);
									
									cancel.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
										@Override
										public void handle(ActionEvent arg0) {
											// this jumps back to the previous screen, but that is actually annoying mostly
//											root.getChildren().clear();
//											root.getChildren().addAll(previous);
//											stage.sizeToScene();
											stage.close();
										}
									});
									create.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
										@SuppressWarnings("unchecked")
										@Override
										public void handle(ActionEvent arg0) {
											String name = basicInformation.getName();
											if (name == null) {
												name = basicInformation.isMainDatabase() ? "Main" : "Database";
												basicInformation.setName(name);
											}
											String correctName = NamingConvention.LOWER_CAMEL_CASE.apply(NamingConvention.UNDERSCORE.apply(name));
											name = correctName;
											try {
												int counter = 0;
												Entry databases = getDatabasesEntry((RepositoryEntry) entry);
												while (databases.getChild(name) != null) {
													name = correctName + ++counter;
												}
												// add the counter to be in sync
												if (counter > 0) {
													basicInformation.setName(basicInformation.getName() + " " + counter);
												}
												basicInformation.setCorrectName(name);
												String idToOpen = create(entry, basicInformation, wizard, properties);
												
												MainController.getInstance().getAsynchronousRemoteServer().reload(entry.getId());
												
												root.getChildren().clear();
												
												stage.hide();
												VBox message = new VBox();
												message.setAlignment(Pos.CENTER);
												message.getChildren().add(MainController.loadGraphic("dialog/dialog-success.png"));
												Label label = new Label("Database successfully set up");
												label.getStyleClass().add("p");
												message.getChildren().add(label);
												root.getChildren().add(message);
												MainController.getInstance().getNotificationHandler().notify("Database successfully set up: " + idToOpen, 5000l, Severity.INFO);
												// it hangs just enough to be noticeable...
												Platform.runLater(new Runnable() {
													@Override
													public void run() {
														MainController.getInstance().getRepositoryBrowser().refresh();
//														stage.sizeToScene();
//														stage.centerOnScreen();
														if (idToOpen != null) {
															MainController.getInstance().open(idToOpen);
														}
													}
												});
											}
											catch (Exception e) {
												stage.hide();
												MainController.getInstance().notify(e);
											}
										}
									});
									stage.sizeToScene();
									stage.centerOnScreen();
								}
								catch (Exception e) {
									MainController.getInstance().notify(e);
								}
							}
						});
					}
					HBox buttons = new HBox();
					buttons.getStyleClass().add("buttons");
					Button close = new Button("Close");
					close.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							stage.close();
						}
					});
					buttons.getChildren().add(close);
					root.getChildren().add(buttons);
					stage.show();
				}
			}, new EntryAcceptor() {
				@Override
				public boolean accept(Entry entry) {
					Collection collection = entry.getCollection();
					return collection != null && "folder".equals(collection.getType()) && "databases".equals(collection.getSubType());
				}
			}));
		}
		return actions;
	}

	private RepositoryEntry createDatabaseEntry(RepositoryEntry project, String name, String prettyName) throws IOException {
		Entry child = getDatabasesEntry(project);
		Entry target = EAIDeveloperUtils.mkdir((RepositoryEntry) child, name);
		CollectionImpl collection = new CollectionImpl();
		collection.setType("database");
		collection.setName(prettyName);
		((RepositoryEntry) target).setCollection(collection);
		((RepositoryEntry) target).saveCollection();
		EAIDeveloperUtils.updated(target.getId());
		RepositoryEntry createNode = ((RepositoryEntry) target).createNode("connection", new JDBCPoolManager(), true);
		EAIDeveloperUtils.created(createNode.getId());
		return createNode;
	}

	private Entry getDatabasesEntry(RepositoryEntry project) throws IOException {
		Entry child = EAIDeveloperUtils.mkdir(project, "databases");
		if (!child.isCollection()) {
			CollectionImpl collection = new CollectionImpl();
			collection.setType("folder");
			collection.setName("Databases");
			collection.setSmallIcon("database-small.png");
			collection.setMediumIcon("database-medium.png");
			collection.setLargeIcon("database-big.png");
			collection.setSubType("databases");
			((RepositoryEntry) child).setCollection(collection);
			((RepositoryEntry) child).saveCollection();
		}
		return child;
	}
	
	public static void setMainContext(JDBCPoolArtifact jdbc, Entry project) {
		// TODO: scrape that no one already has the nabu context!
		// too many manual things required otherwise...?
//		jdbc.getConfig().setContext(project.getId() + ", nabu");
		// too annoying to set the nabu context when dealing with multiple databases...?
		// it will take over all calls in the nabu package like translations, rate limiting...
		jdbc.getConfig().setContext(project.getId());
	}
	
	private <T> String create(Entry project, BasicInformation information, JDBCPoolWizard<T> wizard, T properties) {
		try {
			RepositoryEntry jdbcEntry = createDatabaseEntry((RepositoryEntry) project, information.getCorrectName(), information.getName());
			
			JDBCPoolArtifact jdbc = wizard.apply(project, jdbcEntry, properties, true, information.isMainDatabase());

			// for main databases, we set the context for the project and for nabu as well
			if (information.isMainDatabase()) {
				setMainContext(jdbc, project);
			}
			
			// create data model, we always do this (main database or not)
			RepositoryEntry dataModelEntry = jdbcEntry.getParent().createNode("model", new DataModelManager(), true);
			dataModelEntry.getNode().setName("Model");
			dataModelEntry.saveNode();
			DataModelArtifact model = new DataModelArtifact(dataModelEntry.getId(), dataModelEntry.getContainer(), dataModelEntry.getRepository());
			model.getConfig().setType(DataModelType.DATABASE);
			new DataModelManager().save(dataModelEntry, model);
			EAIDeveloperUtils.created(dataModelEntry.getId());
			
			// if it is a main database, we prefill it with all the necessary things
			if (information.isMainDatabase()) {
				// autosync all collection-named complex types for the main database
				// we don't do this for non-main databases as you are likely not master of the datamodel there!
				// you can still add types to that model from the database
				if (jdbc.getConfig().getManagedModels() == null) {
					jdbc.getConfig().setManagedModels(new ArrayList<DefinedTypeRegistry>());
				}
				jdbc.getConfig().getManagedModels().add(model);
				
				Map<String, DefinedTypeRegistry> definedModelNames = new HashMap<String, DefinedTypeRegistry>();
				for (DefinedTypeRegistry managed : jdbc.getConfig().getManagedModels()) {
					definedModelNames.put(managed.getId(), managed);
				}
				for (DefinedTypeRegistry registry : project.getRepository().getArtifacts(DefinedTypeRegistry.class)) {
					boolean isFromProject = false;
					// if the registry belongs to another project, we don't import it!
					Entry registryEntry = project.getRepository().getEntry(registry.getId());
					while (registryEntry != null) {
						if (EAIRepositoryUtils.isProject(registryEntry)) {
							isFromProject = true;
							break;
						}
						registryEntry = registryEntry.getParent();
					}
					if (isFromProject) {
						continue;
					}
					// check that it is not deprecated, we don't want to start new projects with those
					be.nabu.eai.repository.api.Node node = project.getRepository().getNode(registry.getId());
					// even if the deprecation is in the future, we don't want to add it by default at this point
					if (node != null && node.getDeprecated() != null) {
						continue;
					}
					for (String namespace : registry.getNamespaces()) {
						for (ComplexType potential : registry.getComplexTypes(namespace)) {
							if (!(potential instanceof DefinedType)) {
								continue;
							}
							String collectionName = ValueUtils.getValue(CollectionNameProperty.getInstance(), potential.getProperties());
							if (collectionName != null) {
								if (!definedModelNames.containsKey(registry.getId())) {
									definedModelNames.put(registry.getId(), registry);
									jdbc.getConfig().getManagedModels().add(registry);
								}
								break;
							}
						}
					}
				}
				// if we have both the model and model version of something, remove the model one (we standardize on emodels as concept)
				for (String id : definedModelNames.keySet()) {
					if (id.contains("emodel") && definedModelNames.containsKey(id.replaceAll("\\bemodel\\b", "model"))) {
						jdbc.getConfig().getManagedModels().remove(definedModelNames.get(id.replaceAll("\\bemodel\\b", "model")));
					}
				}
				
				// let's automatically add the translation providers for CMS, by default we assume you will use CMS
				//jdbc.getConfig().setTranslationGet((DefinedService) project.getRepository().resolve("nabu.cms.core.providers.translation.jdbc.get"));
				//jdbc.getConfig().setTranslationSet((DefinedService) project.getRepository().resolve("nabu.cms.core.providers.translation.jdbc.set"));
				jdbc.getConfig().setTranslationGet((DefinedService) project.getRepository().resolve("nabu.cms.core.providers.translation.getById"));
				jdbc.getConfig().setTranslationSet((DefinedService) project.getRepository().resolve("nabu.cms.core.providers.translation.setById"));
			}
			
			new JDBCPoolManager().save((ResourceEntry) project.getRepository().getEntry(jdbc.getId()), jdbc);
			jdbcEntry.getNode().setName("Connection");
			jdbcEntry.saveNode();
			EAIDeveloperUtils.updated(jdbc.getId());
			
			// hard refresh, in local mode it is not working correctly so far, the symptoms are: when you first open it, it is empty until you reload it
			// and it will not appear in the project overview which uses reloading to pick this stuff up
//			((RepositoryEntry) project.getRepository().getEntry(jdbc.getId())).refresh(true, false);
			// it's been internalized in updated()
			
			if (information.isMainDatabase()) {
				synchronize(jdbc);
			}
			else {
				MainController.getInstance().getAsynchronousRemoteServer().reload(jdbc.getId());
			}
			return dataModelEntry.getId();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void synchronize(JDBCPoolArtifact jdbc) {
		// make sure we sync ddls
		// can take a while, we don't care
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					// we will do a synchronous reload of the jdbc pool because we want to sync the datatypes, which is a server-side operation
					MainController.getInstance().getServer().getRemote().reload(jdbc.getId());
					GenerateDatabaseScriptContextMenu.synchronizeManagedTypes(jdbc);
				} 
				catch (Exception e) {
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							MainController.getInstance().notify(e);
						}
					});
				}
			}
		}).start();
	}
}
