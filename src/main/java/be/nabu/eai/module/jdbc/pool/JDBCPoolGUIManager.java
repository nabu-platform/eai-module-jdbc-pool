package be.nabu.eai.module.jdbc.pool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.eai.developer.Main.QuerySheet;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseJAXBComplexGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
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
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class JDBCPoolGUIManager extends BaseJAXBComplexGUIManager<JDBCPoolConfiguration, JDBCPoolArtifact> {

	private Label runLabel;
	private TextField limit;
	private TextField offset;
	private TabPane tabs;

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
		
		tabs = new TabPane();
		tabs.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		tabs.setSide(Side.RIGHT);
		Tab sql = new Tab("SQL");
		tabs.getTabs().add(sql);
		ScrollPane sqlScroll = new ScrollPane();
		sqlScroll.setContent(buildSql(artifact, null));
		sqlScroll.setFitToWidth(true);
		sqlScroll.setFitToHeight(true);
		sql.setContent(sqlScroll);
		
		Tab configuration = new Tab("Configuration");
		configuration.setContent(vbox);
		tabs.getTabs().add(configuration);
		
		pane.getChildren().add(tabs);

		for (QuerySheet sheet : MainController.getAdditionalSheets("sql")) {
			openSheet(artifact, sheet);
		}
		
		AnchorPane.setBottomAnchor(tabs, 0d);
		AnchorPane.setLeftAnchor(tabs, 0d);
		AnchorPane.setRightAnchor(tabs, 0d);
		AnchorPane.setTopAnchor(tabs, 0d);
	}

	private void openSheet(JDBCPoolArtifact artifact, QuerySheet sheet) {
		Tab sql = new Tab(sheet.getName());
		tabs.getTabs().add(sql);
		ScrollPane sqlScroll = new ScrollPane();
		sqlScroll.setContent(buildSql(artifact, sheet.getName()));
		sqlScroll.setFitToWidth(true);
		sqlScroll.setFitToHeight(true);
		sql.setContent(sqlScroll);
	}
	
	private String getQueryToRun(AceEditor editor) {
		String content = editor.getSelection();
		if (content == null || content.trim().isEmpty()) {
			content = editor.getContent();
			
			int start = 0;
			int end = content.length();
			// if the caret is positioned at the very end, it is exactly as big as the content length which we don't want
			long caret = Math.min(content.length() - 1, editor.getCaret());
			boolean lineFeed = false;
			// we don't want to run everything but check for empty lines (or beginning/end)
			for (int i = (int) caret; i >= 0; i--) {
				// we ignore this
				if (content.charAt(i) == '\r') {
					continue;
				}
				if (content.charAt(i) == '\n') {
					if (lineFeed) {
						start = i;
						break;
					}
					else {
						lineFeed = true;
					}
				}
				else {
					lineFeed = false;
				}
			}
			lineFeed = false;
			// we don't want to run everything but check for empty lines (or beginning/end)
			for (int i = (int) caret; i < content.length(); i++) {
				// we ignore this
				if (content.charAt(i) == '\r') {
					continue;
				}
				if (content.charAt(i) == '\n') {
					if (lineFeed) {
						end = i;
						break;
					}
					else {
						lineFeed = true;
					}
				}
				else {
					lineFeed = false;
				}
			}
			content = content.substring(start, end);
		}
		// remove all comments
		content = content.replaceAll("(?m)--.*$", "").trim();
		return content;
	}
	
	public static class BasicSheetInformation {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
	
	private Node buildSql(JDBCPoolArtifact artifact, String sheetName) {
		// keeps track of whether the content has changed
		SimpleBooleanProperty changed = new SimpleBooleanProperty();
		
		SplitPane split = new SplitPane();
		split.setOrientation(Orientation.VERTICAL);
		VBox results = new VBox();
		AceEditor editor = new AceEditor();
		editor.setKeyCombination("CONTROL_ENTERED", new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN));
		editor.subscribe(AceEditor.CHANGE, new EventHandler<Event>() {
			@Override
			public void handle(Event arg0) {
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						changed.set(true);
					}
				});
			}
		});
		QuerySheet sheet = sheetName == null ? MainController.getSheet("sql", "artifact", artifact.getId(), true) : MainController.getSheet("sql", "custom", sheetName, false);
		editor.setContent("text/sql", sheet.getContent() == null ? "" : sheet.getContent());
		
		HBox buttons = new HBox();
		buttons.setPadding(new Insets(10));
		
		editor.subscribe(AceEditor.SAVE, new EventHandler<Event>() {
			@Override
			public void handle(Event arg0) {
				sheet.setContent(editor.getContent());
				MainController.saveConfiguration();
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						changed.set(false);
					}
				});				
			}
		});
		
		Button newSheet = new Button("New sheet");
		newSheet.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				BasicSheetInformation information = new BasicSheetInformation();
				
				// public static Stage buildPopup(String windowTitle, String contentTitle, Object parameters, EventHandler<ActionEvent> ok, boolean refresh) {
				Stage buildPopup = EAIDeveloperUtils.buildPopup("New sheet", "Sheet", information, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						if (information.getName() != null && !information.getName().trim().isEmpty()) {
							QuerySheet existing = MainController.getSheet("sql", "custom", information.getName(), false);
							if (existing == null) {
								QuerySheet newSheet = MainController.newSheet("sql", "custom", information.getName());
								openSheet(artifact, newSheet);
							}
						}
					}
				}, false);
				buildPopup.show();
			}
		});
		buttons.getChildren().add(newSheet);
		
		Button save = new Button("Save SQL");
		save.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				sheet.setContent(editor.getContent());
				MainController.saveConfiguration();
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						changed.set(false);
					}
				});
			}
		});
		save.disableProperty().bind(changed.not());
		buttons.getChildren().addAll(save);
		
		Button run = new Button("Run SQL");
		runLabel = new Label();
		runLabel.setAlignment(Pos.CENTER_LEFT);
		
		HBox.setMargin(runLabel, new Insets(0, 0, 0, 10));
		run.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				String content = getQueryToRun(editor);
				runQuery(artifact, content, results, 0, editor, true);
				arg0.consume();
			}

		});
		buttons.getChildren().addAll(run, runLabel);
		
		HBox spacer = new HBox();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		
		HBox limits = new HBox();
		Label lblLimit = new Label("Limit");
		Label lblOffset = new Label("Offset");
		lblLimit.setAlignment(Pos.CENTER_LEFT);
		lblOffset.setAlignment(Pos.CENTER_LEFT);
		limits.setAlignment(Pos.CENTER_LEFT);
		HBox.setMargin(lblLimit, new Insets(0, 10, 0, 0));
		HBox.setMargin(lblOffset, new Insets(0, 10, 0, 30));
		limit = new TextField();
		limit.setText("" + RunService.AUTO_LIMIT);
		offset = new TextField();
		offset.setText("0");
		limits.getChildren().addAll(lblLimit, limit, lblOffset, offset);
		
		buttons.getChildren().addAll(spacer, limits);
		
		buttons.setAlignment(Pos.CENTER_LEFT);
		editor.subscribe("CONTROL_ENTERED", new EventHandler<Event>() {
			@Override
			public void handle(Event arg0) {
				String content = getQueryToRun(editor);
				runQuery(artifact, content, results, 0, editor, true);
				arg0.consume();
			}
		});
		VBox box = new VBox();
		box.getChildren().addAll(buttons, editor.getWebView());
		split.getItems().addAll(box, results);
		return split;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void runQuery(JDBCPoolArtifact artifact, String query, VBox results, int page, AceEditor editor, boolean first) {
		System.out.println("Running query: " + query);
		results.getChildren().clear();
		if (query != null && !query.trim().isEmpty()) {
			// we find all variable notations
			Pattern pattern = Pattern.compile("\\$\\{[^}]+\\}");
			Matcher matcher = pattern.matcher(query);
			Set<Property<?>> properties = new LinkedHashSet<Property<?>>();
			// we want to keep each name only once, it might be repeated
			List<String> names = new ArrayList<String>();
			while (matcher.find()) {
				// full match
				String variable = matcher.group();
				// remove the syntax
				variable = variable.substring(2, variable.length() - 1).trim();
				if (!names.contains(variable)) {
					properties.add(new SimpleProperty<String>(variable, String.class, false));
					names.add(variable);
				}
			}
			if (!properties.isEmpty()) {
				if (!first) {
					throw new IllegalStateException("The query should not contain any more variables: " + query);
				}
				// get the state to see if we have remembered something
				Map<String, String> state = (Map<String, String>) MainController.getInstance().getState(JDBCPoolGUIManager.class, "variables");
				final Map<String, String> finalState = state == null ? new HashMap<String, String>() : state;
				List<Value<?>> values = new ArrayList<Value<?>>();
				if (state != null) {
					for (Property<?> property : properties) {
						String value = state.get(property.getName());
						if (value != null && !value.trim().isEmpty()) {
							values.add(new ValueImpl(property, value));
						}
					}
				}
				SimplePropertyUpdater updater = new SimplePropertyUpdater(true, properties, values.toArray(new Value[0]));
				EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Set query properties", new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						String rewritten = query;
						for (Property<?> property : properties) {
							Object value = updater.getValue(property.getName());
							String string = value == null || value.toString().trim().isEmpty() ? null : value.toString();
							if (string == null) {
								finalState.remove(property.getName());
								rewritten = rewritten.replaceAll("\\$\\{[\\s]*" + property.getName() + "[\\s]*\\}", "");
							}
							else {
								finalState.put(property.getName(), string);
								rewritten = rewritten.replaceAll("\\$\\{[\\s]*" + property.getName() + "[\\s]*\\}", string);
							}
						}
						MainController.getInstance().setState(JDBCPoolGUIManager.class, "variables", finalState);
						runQuery(artifact, rewritten, results, page, editor, false);
					}
				}, false);
			}
			else {
				MainController.getInstance().offload(new Runnable() {
					@Override
					public void run() {
						Date date = new Date();
						try {
							ComplexContent input = artifact.getServiceInterface().getInputDefinition().newInstance();
							input.set("sql", query);
							if (query.trim().startsWith("select")) {
	//							input.set("limit", RunService.AUTO_LIMIT);
	//							input.set("offset", page * RunService.AUTO_LIMIT);
								if (limit.getText() != null && limit.getText().matches("^[0-9]+$")) {
									input.set("limit", Integer.parseInt(limit.getText()));
								}
								if (offset.getText() != null && offset.getText().matches("^[0-9]+$")) {
									input.set("offset", Integer.parseInt(offset.getText()));
								}
							}
							Future<ServiceResult> run = artifact.getRepository().getServiceRunner().run(artifact, artifact.getRepository().newExecutionContext(SystemPrincipal.ROOT), input);
							ServiceResult serviceResult = run.get();
							ComplexContent output = serviceResult.getOutput();
							// we probably weren't able to parse it, get the stringified version (check RemoteServer to see how this is done)
							Object object = output == null ? null : output.get("content");
							// when running locally, you get a parsed result from the server which is a list of objects, we stringify it so it follows the same paradigm
							if (object == null && output != null && output.get("results") != null) {
								XMLBinding xmlBinding = new XMLBinding(output.getType(), Charset.defaultCharset());
								ByteArrayOutputStream stream = new ByteArrayOutputStream();
								xmlBinding.marshal(stream, output);
								object = new String(stream.toByteArray());
							}
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
							e.printStackTrace();
							MainController.getInstance().notify(e);
						}
						finally {
							Platform.runLater(new Runnable() {
								@Override
								public void run() {
									runLabel.setText("Run in: " + (new Date().getTime() - date.getTime()) + "ms");
									editor.requestFocus();
								}
							});
						}
					}
				}, true, "Running: " + query);
			}
		}
	}
	
	@Override
	protected BaseArtifactGUIInstance<JDBCPoolArtifact> newGUIInstance(Entry entry) {
		return new BaseArtifactGUIInstance<JDBCPoolArtifact>(this, entry) {
			@Override
			public List<Validation<?>> save() throws IOException {
				// if you have configured a pool proxy, we copy the settings
				// visually you can't edit the settings anyway and there is a high likelihood that you will use the same database (so same dialect, driver etc) in all environments
				// this means you probably only need to differentiate those particular settings
				// the alternative is that we show all fields though they don't do anything if you have a proxy, simply to optimize the deployment process (preventing the need to fill in the details then)
				if (getArtifact().getConfig().getPoolProxy() != null) {
					getArtifact().getConfig().setAutoCommit(getArtifact().getConfig().getPoolProxy().getConfig().getAutoCommit());
					getArtifact().getConfig().setConnectionTimeout(getArtifact().getConfig().getPoolProxy().getConfig().getConnectionTimeout());
					getArtifact().getConfig().setDialect(getArtifact().getConfig().getPoolProxy().getConfig().getDialect());
					getArtifact().getConfig().setDriverClassName(getArtifact().getConfig().getPoolProxy().getConfig().getDriverClassName());
					getArtifact().getConfig().setEnableMetrics(getArtifact().getConfig().getPoolProxy().getConfig().getEnableMetrics());
					getArtifact().getConfig().setIdleTimeout(getArtifact().getConfig().getPoolProxy().getConfig().getIdleTimeout());
					getArtifact().getConfig().setJdbcUrl(getArtifact().getConfig().getPoolProxy().getConfig().getJdbcUrl());
					getArtifact().getConfig().setMaximumPoolSize(getArtifact().getConfig().getPoolProxy().getConfig().getMaximumPoolSize());
					getArtifact().getConfig().setMaxLifetime(getArtifact().getConfig().getPoolProxy().getConfig().getMaxLifetime());
					getArtifact().getConfig().setMinimumIdle(getArtifact().getConfig().getPoolProxy().getConfig().getMinimumIdle());
					getArtifact().getConfig().setPassword(getArtifact().getConfig().getPoolProxy().getConfig().getPassword());
					getArtifact().getConfig().setUsername(getArtifact().getConfig().getPoolProxy().getConfig().getUsername());
				}
				// TODO: start an asynchronous task to synchronize jdbc
				return super.save();
			}
		};
	}
}
