package be.nabu.eai.module.jdbc.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.module.jdbc.pool.JDBCPoolManager;
import be.nabu.eai.module.jdbc.pool.JDBCPoolUtils;
import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.structure.DefinedStructure;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import nabu.protocols.jdbc.pool.types.TableDescription;

public class GenerateDatabaseStructuresContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		if (!entry.isLeaf()) {
			MenuItem generate = new MenuItem("New structure from database");
			generate.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					SimpleProperty<JDBCPoolArtifact> pool = new SimpleProperty<JDBCPoolArtifact>("JDBC Pool", JDBCPoolArtifact.class, true);
					SimplePropertyUpdater updater = new SimplePropertyUpdater(true, new LinkedHashSet(Arrays.asList(pool)));
					EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Choose JDBC Pool", new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							try {
								JDBCPoolArtifact pool = updater.getValue("JDBC Pool");
								if (pool != null) {
									generateStructure(entry, pool);
								}
							}
							catch (Exception e) {
								MainController.getInstance().notify(e);
							}
						}

					});
				}
			});
			return generate;
		}
		return null;
	}

	public static void generateStructure(Entry entry, JDBCPoolArtifact pool) throws InterruptedException, ExecutionException {
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
				VBox box = new VBox();
				List<TableDescription> chosen = new ArrayList<TableDescription>();
				List<CheckBox> checks = new ArrayList<CheckBox>();
				for (Object object : objects) {
					TableDescription description = object instanceof TableDescription ? (TableDescription) object : TypeUtils.getAsBean((ComplexContent) object, TableDescription.class);
					CheckBox check = new CheckBox(description.getSchema() + "." + description.getName());
					String localName = NamingConvention.LOWER_CAMEL_CASE.apply(description.getName());
					check.setDisable(entry.getChild(localName) != null);
					check.selectedProperty().addListener(new ChangeListener<Boolean>() {
						@Override
						public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
							if (arg2 != null && arg2) {
								if (!chosen.contains(description)) {
									chosen.add(description);
								}
							}
							else {
								chosen.remove(description);
							}
						}
					});
					check.setPadding(new Insets(5, 0, 5, 0));
					box.getChildren().add(check);
					checks.add(check);
				}
				HBox buttons = new HBox();
				buttons.setPadding(new Insets(10));
				buttons.setAlignment(Pos.CENTER);
				Button cancel = new Button("Cancel");
				Button create = new Button("Create");
				buttons.getChildren().addAll(create, cancel);
				
				ScrollPane pane = new ScrollPane();
				pane.setPrefWidth(800);
				pane.setPrefHeight(600);
				pane.setContent(box);
				
				TextField field = new TextField();
				field.textProperty().addListener(new ChangeListener<String>() {
					@Override
					public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
						for (CheckBox check : checks) {
							if (arg2 == null || arg2.trim().isEmpty() || check.getText().toLowerCase().contains(arg2.trim().toLowerCase())) {
								check.setVisible(true);
								check.setManaged(true);
							}
							else {
								check.setVisible(false);
								check.setManaged(false);
							}
						}
					}
				});
				VBox.setMargin(field, new Insets(10, 0, 10, 0));
				VBox container = new VBox();
				container.setPadding(new Insets(10));
				CheckBox automanage = new CheckBox("Automatically manage types");
				// can't automanage if we can't update
				automanage.setSelected(GenerateDatabaseScriptContextMenu.canEdit(pool.getId()));
				// don't allow toggling on if we can't update
				automanage.setDisable(!automanage.isSelected());
				container.getChildren().addAll(field, pane, automanage, buttons);
				Stage stage = EAIDeveloperUtils.buildPopup("Generate structures for tables", container, MainController.getInstance().getActiveStage(), null, true);
				
				cancel.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						stage.close();
					}
				});
				create.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						stage.close();
						for (TableDescription description : chosen) {
							DefinedStructure structure = new DefinedStructure();
							String localName = NamingConvention.LOWER_CAMEL_CASE.apply(description.getName());
							structure.setId(entry.getId() + "." + localName);
							structure.setName(localName);
							JDBCPoolUtils.toType(structure, description);
							StructureManager manager = new StructureManager();
							try {
								RepositoryEntry repositoryEntry = ((RepositoryEntry) entry).createNode(localName, manager, true);
								manager.saveContent(repositoryEntry, structure);
								MainController.getInstance().getRepositoryBrowser().refresh();
							}
							catch (Exception e) {
								MainController.getInstance().notify(e);
							}
							if (automanage.isSelected()) {
								if (pool.getConfig().getManagedTypes() != null) {
									pool.getConfig().setManagedTypes(new ArrayList<DefinedType>());
								}
								pool.getConfig().getManagedTypes().add(structure);
							}
						}
						JDBCPoolUtils.relink(pool, chosen);
						if (automanage.isSelected()) {
							try {
								new JDBCPoolManager().save((ResourceEntry) entry.getRepository().getEntry(pool.getId()), pool);
								MainController.getInstance().getServer().getRemote().reload(pool.getId());
								MainController.getInstance().getCollaborationClient().updated(pool.getId(), "Added managed types");
							}
							catch (Exception e) {
								MainController.getInstance().notify(e);
							}
						}
					}
				});
			}
		}
	}
}
