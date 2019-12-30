package saker.build.ide.eclipse.extension.script.outline;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;

public interface IScriptOutlineEntry {
	public List<? extends IScriptOutlineEntry> getChildren();

	public String getLabel();

	public String getType();

	public Image getWidgetImage();

	public void setWidgetImage(Image image);

	public StyledString getWidgetLabel();

	public void setWidgetLabel(StyledString label);

	public String getSchemaIdentifier();

	public Map<String, String> getSchemaMetaData();
}
