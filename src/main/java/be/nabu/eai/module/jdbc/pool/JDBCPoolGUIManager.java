package be.nabu.eai.module.jdbc.pool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseJAXBComplexGUIManager;
import be.nabu.eai.developer.util.RunService;
import be.nabu.eai.module.types.structure.GenerateXSDMenuEntry;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.jfx.control.ace.AceEditor;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.Validation;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class JDBCPoolGUIManager extends BaseJAXBComplexGUIManager<JDBCPoolConfiguration, JDBCPoolArtifact> {

	private Label runLabel;

	public JDBCPoolGUIManager() {
		super("JDBC Pool", JDBCPoolArtifact.class, new JDBCPoolManager(), JDBCPoolConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected JDBCPoolArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new JDBCPoolArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
	}

	@Override
	public String getCategory() {
		return "Protocols";
	}

	@Override
	public void display(MainController controller, AnchorPane pane, JDBCPoolArtifact artifact) throws IOException, ParseException {
		VBox vbox = new VBox();
		AnchorPane anchor = new AnchorPane();
		super.display(controller, anchor, artifact);
		vbox.getChildren().addAll(anchor);
		
		TabPane tabs = new TabPane();
		tabs.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		tabs.setSide(Side.RIGHT);
		Tab sql = new Tab("SQL");
		tabs.getTabs().add(sql);
		ScrollPane sqlScroll = new ScrollPane();
		sqlScroll.setContent(buildSql(artifact));
		sqlScroll.setFitToWidth(true);
		sqlScroll.setFitToHeight(true);
		sql.setContent(sqlScroll);
		
		Tab configuration = new Tab("Configuration");
		configuration.setContent(vbox);
		tabs.getTabs().add(configuration);
		
		pane.getChildren().add(tabs);
		
		AnchorPane.setBottomAnchor(tabs, 0d);
		AnchorPane.setLeftAnchor(tabs, 0d);
		AnchorPane.setRightAnchor(tabs, 0d);
		AnchorPane.setTopAnchor(tabs, 0d);
	}
	
	private Node buildSql(JDBCPoolArtifact artifact) {
		SplitPane split = new SplitPane();
		split.setOrientation(Orientation.VERTICAL);
		VBox results = new VBox();
		AceEditor editor = new AceEditor();
		editor.setKeyCombination("CONTROL_ENTERED", new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN));
		editor.setContent("text/sql", "");
		HBox buttons = new HBox();
		buttons.setPadding(new Insets(10));
		Button run = new Button("Run SQL");
		runLabel = new Label();
		runLabel.setAlignment(Pos.CENTER_LEFT);	
		HBox.setMargin(runLabel, new Insets(0, 0, 0, 10));
		run.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				String content = editor.getSelection();
				if (content == null || content.trim().isEmpty()) {
					content = editor.getContent();
				}
				runQuery(artifact, content, results, 0);
				arg0.consume();
			}
		});
		buttons.getChildren().addAll(run, runLabel);
		buttons.setAlignment(Pos.CENTER_LEFT);
		editor.subscribe("CONTROL_ENTERED", new EventHandler<Event>() {
			@Override
			public void handle(Event arg0) {
				String content = editor.getSelection();
				if (content == null || content.trim().isEmpty()) {
					content = editor.getContent();
				}
				runQuery(artifact, content, results, 0);
				arg0.consume();
			}
		});
		VBox box = new VBox();
		box.getChildren().addAll(buttons, editor.getWebView());
		split.getItems().addAll(box, results);
		return split;
	}
	
	private void runQuery(JDBCPoolArtifact artifact, String query, VBox results, int page) {
		results.getChildren().clear();
		if (query != null && !query.trim().isEmpty()) {
			MainController.getInstance().offload(new Runnable() {
				@Override
				public void run() {
					Date date = new Date();
					try {
						ComplexContent input = artifact.getServiceInterface().getInputDefinition().newInstance();
						input.set("sql", query);
						input.set("limit", RunService.AUTO_LIMIT);
						input.set("offset", page * RunService.AUTO_LIMIT);
						Future<ServiceResult> run = artifact.getRepository().getServiceRunner().run(artifact, artifact.getRepository().newExecutionContext(SystemPrincipal.ROOT), input);
						ServiceResult serviceResult = run.get();
						ComplexContent output = serviceResult.getOutput();
						// we probably weren't able to parse it, get the stringified version (check RemoteServer to see how this is done)
						Object object = output == null ? null : output.get("content");
						if (object != null) {
							Structure content = GenerateXSDMenuEntry.generateFromXML(object.toString(), Charset.forName("UTF-8"));
							Element<?> element = content.get("results");
							// if we have a results element, make sure it's a list, if there is only one hit, it will not be autogenerated into a list but a singular element
							if (element != null) {
								element.setProperty(new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0));
							}
							XMLBinding binding = new XMLBinding(content, Charset.forName("UTF-8"));
							ComplexContent unmarshal = binding.unmarshal(new ByteArrayInputStream(object.toString().getBytes(Charset.forName("UTF-8"))), new Window[0]);
							Platform.runLater(new Runnable() {
								@Override
								public void run() {
									MainController.getInstance().showContent(results, unmarshal, null);
								}
							});
						}
						else if (serviceResult.getException() != null) {
							Label emptyLabel = new Label("Your query could not be run correctly: " + serviceResult.getException().getMessage());
							emptyLabel.setPadding(new Insets(10));
							Platform.runLater(new Runnable() {
								@Override
								public void run() {
									results.getChildren().add(emptyLabel);
								}
							});
						}
						else {
							Label emptyLabel = new Label("No results for this query");
							emptyLabel.setPadding(new Insets(10));
							Platform.runLater(new Runnable() {
								@Override
								public void run() {
									results.getChildren().add(emptyLabel);
								}
							});
						}
					}
					catch (Exception e) {
						MainController.getInstance().notify(e);
					}
					finally {
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								runLabel.setText("Run in: " + (new Date().getTime() - date.getTime()) + "ms");
							}
						});
					}
				}
			}, true, "Running: " + query);
		}
	}
	
	@Override
	protected BaseArtifactGUIInstance<JDBCPoolArtifact> newGUIInstance(Entry entry) {
		return new BaseArtifactGUIInstance<JDBCPoolArtifact>(this, entry) {
			@Override
			public List<Validation<?>> save() throws IOException {
				// TODO: start an asynchronous task to synchronize jdbc
				return super.save();
			}
		};
	}
}
