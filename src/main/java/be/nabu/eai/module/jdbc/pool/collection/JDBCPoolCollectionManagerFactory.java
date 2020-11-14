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
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeRegistry;
import be.nabu.libs.types.properties.CollectionNameProperty;
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
		Collection collection = entry.getCollection();
		if (collection != null && collection.getType().equals("database")) {
			return new JDBCPoolCollectionManager(entry);
		}
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
		Collection collection = entry.getCollection();
		if (collection != null && collection.getType().equals("project")) {
			VBox box = new VBox();
			box.getStyleClass().add("collection-action");
			Label title = new Label("Add Database");
			title.getStyleClass().add("collection-action-title");
			box.getChildren().addAll(MainController.loadFixedSizeGraphic("database-big.png", 64), title);
			actions.add(new CollectionActionImpl(box, new EventHandler<ActionEvent>() {
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
						
						box.getStyleClass().add("collection-action");
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
											root.getChildren().clear();
											root.getChildren().addAll(previous);
											stage.sizeToScene();
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
												create(entry, basicInformation, wizard, properties);
												
												MainController.getInstance().getAsynchronousRemoteServer().reload(entry.getId());
												
												root.getChildren().clear();
												
												VBox message = new VBox();
												message.setAlignment(Pos.CENTER);
												message.getChildren().add(MainController.loadGraphic("dialog/dialog-success.png"));
												Label label = new Label("Database successfully set up");
												label.getStyleClass().add("p");
												message.getChildren().add(label);
												root.getChildren().add(message);
												// it hangs just enough to be noticeable...
												Platform.runLater(new Runnable() {
													@Override
													public void run() {
														MainController.getInstance().getRepositoryBrowser().refresh();
														stage.sizeToScene();
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
		EAIDeveloperUtils.reload(child.getId());
		return ((RepositoryEntry) target).createNode("connection", new JDBCPoolManager(), true);
	}

	private Entry getDatabasesEntry(RepositoryEntry project) throws IOException {
		Entry child = EAIDeveloperUtils.mkdir(project, "database");
		if (!child.isCollection()) {
			CollectionImpl collection = new CollectionImpl();
			collection.setType("folder");
			collection.setName("Database");
			((RepositoryEntry) child).setCollection(collection);
			((RepositoryEntry) child).saveCollection();
		}
		return child;
	}
	
	private <T> void create(Entry project, BasicInformation information, JDBCPoolWizard<T> wizard, T properties) {
		try {
			RepositoryEntry jdbcEntry = createDatabaseEntry((RepositoryEntry) project, information.getCorrectName(), information.getName());
			
			JDBCPoolArtifact jdbc = wizard.apply(project, jdbcEntry, properties, true, information.isMainDatabase());
			
			// create data model, we always do this (main database or not)
			RepositoryEntry dataModelEntry = jdbcEntry.getParent().createNode("model", new DataModelManager(), true);
			dataModelEntry.getNode().setName("Model");
			dataModelEntry.saveNode();
			DataModelArtifact model = new DataModelArtifact(dataModelEntry.getId(), dataModelEntry.getContainer(), dataModelEntry.getRepository());
			model.getConfig().setType(DataModelType.DATABASE);
			new DataModelManager().save(dataModelEntry, model);
			EAIDeveloperUtils.updated(jdbcEntry.getId());
			
			// if it is a main database, we prefill it with all the necessary things
			if (information.isMainDatabase()) {
				// autosync all collection-named complex types
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
			}
			
			new JDBCPoolManager().save((ResourceEntry) project.getRepository().getEntry(jdbc.getId()), jdbc);
			jdbcEntry.getNode().setName("Connection");
			jdbcEntry.saveNode();
			
			if (information.isMainDatabase()) {
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
			else {
				MainController.getInstance().getAsynchronousRemoteServer().reload(jdbc.getId());
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
