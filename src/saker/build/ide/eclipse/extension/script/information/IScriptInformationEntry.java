package saker.build.ide.eclipse.extension.script.information;

import java.util.Map;

public interface IScriptInformationEntry {
	public String getTitle();

	public String getSubTitle();

	public void setTitle(String title);

	public void setSubTitle(String subtitle);

	//doc: for <img src tag>, must not contain quotes
	//     probably something like "data:image/png;base64, "
	public void setIconSource(String sourceurl) throws IllegalArgumentException;

	public String getSchemaIdentifier();

	public Map<String, String> getSchemaMetaData();
}
