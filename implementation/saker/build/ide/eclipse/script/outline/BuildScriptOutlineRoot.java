package saker.build.ide.eclipse.script.outline;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineRoot;
import saker.build.scripting.model.ScriptStructureOutline;
import saker.build.scripting.model.StructureOutlineEntry;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;

public class BuildScriptOutlineRoot implements IScriptOutlineRoot {
	private ScriptStructureOutline outline;
	private LazySupplier<List<? extends BuildScriptOutlineEntry>> rootEntrySupplier;

	public BuildScriptOutlineRoot(ScriptStructureOutline outline) {
		this.outline = outline;

		this.rootEntrySupplier = LazySupplier.of(() -> {
			List<? extends StructureOutlineEntry> roots = outline.getRootEntries();
			if (ObjectUtils.isNullOrEmpty(roots)) {
				return Collections.emptyList();
			}

			BuildScriptOutlineEntry[] res = new BuildScriptOutlineEntry[roots.size()];
			int i = 0;
			for (StructureOutlineEntry childentry : roots) {
				res[i++] = new BuildScriptOutlineEntry(childentry);
			}
			return ImmutableUtils.unmodifiableArrayList(res);
		});
	}

	@Override
	public List<? extends BuildScriptOutlineEntry> getRootEntries() {
		return rootEntrySupplier.get();
	}

	@Override
	public String getSchemaIdentifier() {
		return outline.getSchemaIdentifier();
	}

	@Override
	public Map<String, String> getSchemaMetaData() {
		return outline.getSchemaMetaData();
	}

}
