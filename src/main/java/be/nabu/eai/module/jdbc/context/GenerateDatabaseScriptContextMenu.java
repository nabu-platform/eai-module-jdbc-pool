package be.nabu.eai.module.jdbc.context;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.util.Confirm;
import be.nabu.eai.developer.util.Confirm.ConfirmType;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.Entry;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.api.ComplexType;

public class GenerateDatabaseScriptContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		if (entry.isNode() && ComplexType.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
			Menu menu = new Menu("Create SQL");
			for (final Class<SQLDialect> clazz : EAIRepositoryUtils.getImplementationsFor(entry.getRepository().getClassLoader(), SQLDialect.class)) {
				MenuItem item = new MenuItem(clazz.getSimpleName());
				item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
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
				menu.getItems().add(item);
			}
			return menu;
		}
		return null;
	}

}
