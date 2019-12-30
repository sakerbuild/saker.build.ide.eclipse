package saker.build.ide.eclipse.extension.script.outline;

import java.util.List;
import java.util.Map;

public interface IScriptOutlineRoot {
	public List<? extends IScriptOutlineEntry> getRootEntries();

	public String getSchemaIdentifier();

	public Map<String, String> getSchemaMetaData();
}
