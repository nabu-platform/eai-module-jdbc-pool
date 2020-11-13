package be.nabu.eai.module.jdbc.pool.collection;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.CollectionManager;
import be.nabu.eai.developer.impl.CustomTooltip;
import be.nabu.eai.module.data.model.DataModelArtifact;
import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.repository.api.Entry;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class JDBCPoolCollectionManager implements CollectionManager {

	private Entry entry;

	public JDBCPoolCollectionManager(Entry entry) {
		this.entry = entry;
	}

	@Override
	public boolean hasSummaryView() {
		return true;
	}

	@Override
	public Node getSummaryView() {
		VBox box = new VBox();
		box.getStyleClass().addAll("collection-summary");
		box.setAlignment(Pos.CENTER);
		Label title = new Label(entry.getCollection().getName() == null ? entry.getName() : entry.getCollection().getName());
		title.getStyleClass().add("collection-title");
		box.getChildren().addAll(title, MainController.loadFixedSizeGraphic("database-big.png", 64));
		
		HBox buttons = new HBox();
		buttons.getStyleClass().add("collection-buttons");
		box.getChildren().add(buttons);
		
		Button openConnection = new Button();
		openConnection.setGraphic(MainController.loadFixedSizeGraphic("icons/search.png"));
		new CustomTooltip("Browse the database contents").install(openConnection);
		buttons.getChildren().add(openConnection);
		
		Button openModel = new Button();
		openModel.setGraphic(MainController.loadFixedSizeGraphic("datamodel.png"));
		new CustomTooltip("View the data model").install(openModel);
		buttons.getChildren().add(openModel);
		
		Button edit = new Button();
		edit.setGraphic(MainController.loadFixedSizeGraphic("icons/edit.png", 16));
		new CustomTooltip("Edit the database details").install(edit);
		buttons.getChildren().add(edit);
		
		Button remove = new Button();
		remove.setGraphic(MainController.loadFixedSizeGraphic("icons/delete.png", 16));
		new CustomTooltip("Remove the database").install(remove);
		buttons.getChildren().add(remove);
		
		JDBCPoolArtifact chosen = null;
		for (JDBCPoolArtifact pool : entry.getRepository().getArtifacts(JDBCPoolArtifact.class)) {
			if (pool.getId().startsWith(entry.getId() + ".")) {
				chosen = pool;
				break;
			}
		}
		
		JDBCPoolArtifact chosenFinal = chosen;
		openConnection.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				MainController.getInstance().open(chosenFinal.getId());
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
	
}
