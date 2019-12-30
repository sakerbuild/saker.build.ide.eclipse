package saker.build.ide.eclipse.extension.script.information;

import java.util.List;
import java.util.Map;

public interface IScriptInformationRoot {
	public List<? extends IScriptInformationEntry> getEntries();

	public String getSchemaIdentifier();

	public Map<String, String> getSchemaMetaData();

}
