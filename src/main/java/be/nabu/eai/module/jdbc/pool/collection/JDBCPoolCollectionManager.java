package be.nabu.eai.module.jdbc.pool.collection;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.CollectionManager;
import be.nabu.eai.developer.collection.EAICollectionUtils;
import be.nabu.eai.developer.impl.CustomTooltip;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.Confirm;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.developer.util.Confirm.ConfirmType;
import be.nabu.eai.module.data.model.DataModelArtifact;
import be.nabu.eai.module.jdbc.context.GenerateDatabaseScriptContextMenu;
import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.module.jdbc.pool.JDBCPoolManager;
import be.nabu.eai.module.jdbc.pool.api.JDBCPoolWizard;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.types.api.DefinedType;
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

public class JDBCPoolCollectionManager implements CollectionManager {

	private Entry entry;

	public JDBCPoolCollectionManager(Entry entry) {
		this.entry = entry;
		// it triggers waaaaay too often to be useful
//		JDBCPoolArtifact pool = getPool();
//		if (pool != null) {
//			// we wait 5seconds before synchronizing
//			// this is twofold: you might do multiple changes, we want to bundle the reload
//			// if we reload too fast, the "unloading" of a delete for example is still ongoing, this goes into massive error mode when using the optimized localhost connection of developer (as file system changes need to pass through the unload...)
//			MainController.getInstance().submitTask("jdbc-sync-" + pool.getId(), "Synchronize JDBC Tables", new Runnable() {
//				@Override
//				public void run() {
//					try {
//						System.out.println("---------> synchronizing jdbc pool " + pool.getId());
//						GenerateDatabaseScriptContextMenu.synchronizeManagedTypes(pool);
//					}
//					catch (Exception e) {
//						MainController.getInstance().notify(e);
//					}
//				}
//			}, 5000);
//		}
	}

	@Override
	public boolean hasSummaryView() {
		return true;
	}

	@Override
	public Node getSummaryView() {
		JDBCPoolArtifact chosen = getPool();
		// no pool found!
		if (chosen == null) {
			return null;
		}
		
		JDBCPoolWizard chosenWizard = getWizard(chosen);
		// not a supported type!
		if (chosenWizard == null) {
			return null;
		}
		
		VBox box = getLargeIcon(chosenWizard);
		box.getStyleClass().add("tile-medium");
		HBox buttons = new HBox();
		buttons.getStyleClass().add("collection-buttons");
		box.getChildren().add(buttons);
		
		Button openConnection = new Button();
		openConnection.setGraphic(MainController.loadFixedSizeGraphic("icons/search.png", 12));
		new CustomTooltip("Browse the database contents").install(openConnection);
		buttons.getChildren().add(openConnection);
		
		Button openModel = new Button();
		openModel.setGraphic(MainController.loadFixedSizeGraphic("datamodel.png", 12));
		new CustomTooltip("View the data model").install(openModel);
		buttons.getChildren().add(openModel);
		
		Button edit = new Button();
		edit.setGraphic(MainController.loadFixedSizeGraphic("icons/edit.png", 12));
		new CustomTooltip("Edit the database details").install(edit);
		buttons.getChildren().add(edit);
		
		List<DefinedType> managedTypes = chosen.getManagedTypes();
		if (managedTypes != null && !managedTypes.isEmpty()) {
			Button synchronize = new Button();
			synchronize.setGraphic(MainController.loadFixedSizeGraphic("icons/reload.png", 12));
			new CustomTooltip("Synchronize managed types to the database").install(synchronize);
			buttons.getChildren().add(synchronize);
			synchronize.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					try {
						GenerateDatabaseScriptContextMenu.synchronizeManagedTypes(chosen);
					}
					catch (Exception e) {
						MainController.getInstance().notify(e);
					}					
				}
			});
		}
		
		Entry project = EAICollectionUtils.getProject(entry);
		
		edit.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				try {
					String originalName = entry.getCollection() == null || entry.getCollection().getName() == null ? entry.getName() : entry.getCollection().getName();
					VBox root = new VBox();
					Stage stage = EAIDeveloperUtils.buildPopup("Configure Database", root, MainController.getInstance().getStage(), StageStyle.DECORATED, false);
					root.getStyleClass().addAll("collection-form", "project");
					
					Label wizardTitle = new Label("Configure your database");
					wizardTitle.getStyleClass().add("h2");
					root.getChildren().add(wizardTitle);
					
					if (chosenWizard != null) {
						BasicInformation basicInformation = new BasicInformation();
						basicInformation.setHideMainOption(true);
						basicInformation.setName(originalName);
						basicInformation.setCorrectName(entry.getName());
						VBox general = new VBox();
						SimplePropertyUpdater updater = EAIDeveloperUtils.createUpdater(basicInformation, null);
						MainController.getInstance().showProperties(updater, general, true);
						Separator separator = new Separator(Orientation.HORIZONTAL);
						VBox.setMargin(separator, new Insets(20, 0, 20, 0));
						root.getChildren().addAll(general, separator);
						
						Object properties = chosenWizard.load(chosen);
						VBox target = new VBox();
						updater = EAIDeveloperUtils.createUpdater(properties, null);
						MainController.getInstance().showProperties(updater, target, true);
						root.getChildren().add(target);
						Button update = new Button("Update");
						Button cancel = new Button("Cancel");
						HBox buttons = new HBox();
						update.getStyleClass().add("primary");
						buttons.getStyleClass().add("buttons");
						buttons.getChildren().addAll(update, cancel);
						root.getChildren().add(buttons);
						cancel.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent arg0) {
								stage.close();
							}
						});
						update.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
							@SuppressWarnings("unchecked")
							@Override
							public void handle(ActionEvent arg0) {
								// first we update any settings you might have
								Entry jdbcEntry = entry.getRepository().getEntry(chosen.getId());
								boolean isMain = chosen.getConfig().getContext() != null && Arrays.asList(chosen.getConfig().getContext().split("[\\s]*,[\\s]*")).contains(project.getId());
								// keep track of the origianl jdbc connection, if it changes we may need to trigger a resync
								String originalJdbc = chosen.getConfig().getJdbcUrl();
								JDBCPoolArtifact applied = chosenWizard.apply(project, (RepositoryEntry) jdbcEntry, properties, false, isMain);
								try {
									new JDBCPoolManager().save((ResourceEntry) jdbcEntry, applied);
									EAIDeveloperUtils.updated(jdbcEntry.getId());
									
									// then we do a rename (if necessary), cause that will cause a refresh, we need a new jdbc pool etc etc
									// you renamed it! damn you!
									if (originalName == null && basicInformation.getName() != null || (originalName != null && !originalName.equals(basicInformation.getName()))) {
										String rename = MainController.getInstance().rename((ResourceEntry) entry, basicInformation.getName() == null || basicInformation.getName().trim().isEmpty() ? "Database" : basicInformation.getName());
										entry = entry.getParent().getChild(rename);
									}
									
									// we do the synchronize after any potential renames! so the server sees the correct shizzle
									if (isMain && applied.getConfig().getJdbcUrl() != null && !applied.getConfig().getJdbcUrl().equals(originalJdbc)) {
										JDBCPoolCollectionManagerFactory.synchronize(applied);
									}
								}
								catch (IOException e) {
									MainController.getInstance().notify(e);
								}
								
								stage.close();
							}
						});
						stage.show();
					}
					
				}
				catch (Exception e) {
					MainController.getInstance().notify(e);
				}
			}
		});
		edit.setDisable(chosen == null || chosenWizard == null);
		
		Button remove = new Button();
		remove.setGraphic(MainController.loadFixedSizeGraphic("icons/delete.png", 12));
		new CustomTooltip("Remove the database").install(remove);
		buttons.getChildren().add(remove);
		remove.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				Confirm.confirm(ConfirmType.WARNING, "Delete " + entry.getName(), "Are you sure you want to delete this database connection? This action can not be undone.", new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						EAIDeveloperUtils.delete(entry.getId());
					}
				});
			}
		});
		
		openConnection.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				MainController.getInstance().open(chosen.getId());
			}
		});
		// if no chosen, we can't open it
		openConnection.setDisable(chosen == null);
		
		DataModelArtifact chosenModel = null;
		for (DataModelArtifact model : entry.getRepository().getArtifacts(DataModelArtifact.class)) {
			if (model.getId().startsWith(entry.getId() + ".")) {
				chosenModel = model;
				break;
			}
		}
		
		DataModelArtifact chosenModelFinal = chosenModel;
		openModel.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				MainController.getInstance().open(chosenModelFinal.getId());
			}
		});
		// if no chosen, we can't open it
		openModel.setDisable(chosenModelFinal == null);
		
		return box;
	}

	private JDBCPoolWizard getWizard(JDBCPoolArtifact chosen) {
		JDBCPoolWizard chosenWizard = null;
		for (JDBCPoolWizard wizard : JDBCPoolCollectionManagerFactory.getPoolWizards()) {
			Object properties = wizard.load(chosen);
			// we gots us the correct wizard!
			if (properties != null) {
				chosenWizard = wizard;
				break;
			}
		}
		return chosenWizard;
	}

	private JDBCPoolArtifact getPool() {
		JDBCPoolArtifact chosen = null;
		for (JDBCPoolArtifact pool : entry.getRepository().getArtifacts(JDBCPoolArtifact.class)) {
			if (pool.getId().startsWith(entry.getId() + ".")) {
				chosen = pool;
				break;
			}
		}
		return chosen;
	}

	private VBox getLargeIcon(JDBCPoolWizard chosenWizardFinal) {
		VBox box = new VBox();
		box.getStyleClass().addAll("collection-summary");
		box.setAlignment(Pos.CENTER);
		Label title = new Label(entry.getCollection() == null || entry.getCollection().getName() == null ? entry.getName() : entry.getCollection().getName());
		title.getStyleClass().add("collection-title");
		box.getChildren().addAll(title, MainController.loadFixedSizeGraphic(chosenWizardFinal == null || chosenWizardFinal.getIcon() == null ? "database-big.png" : chosenWizardFinal.getIcon(), 64));
		if (chosenWizardFinal != null) {
			Label driver = new Label(chosenWizardFinal.getName());
			driver.getStyleClass().add("subscript");
			box.getChildren().add(driver);
		}
		return box;
	}

	@Override
	public boolean hasLargeIcon() {
		return true;
	}

	@Override
	public Node getLargeIcon() {
		JDBCPoolArtifact pool = getPool();
		if (pool == null) {
			return null;
		}
		JDBCPoolWizard wizard = getWizard(pool);
		if (wizard == null) {
			return null;
		}
		return getLargeIcon(wizard);
	}

	@Override
	public Entry getEntry() {
		return entry;
	}
	
}
