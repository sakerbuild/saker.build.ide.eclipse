package saker.build.ide.eclipse.script.information;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import saker.build.ide.eclipse.extension.script.information.IScriptInformationEntry;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.TextPartition;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;

public class BuildScriptScriptInformationEntry implements IScriptInformationEntry {
	private final String schemaIdentifier;
	private final Map<String, String> schemaMetaData;

	private Supplier<String> title;
	private Supplier<String> subTitle;
	private String iconSource;
	private Supplier<? extends FormattedTextContent> content;

	public BuildScriptScriptInformationEntry(TextPartition partition) {
		this.title = partition::getTitle;
		this.subTitle = partition::getSubTitle;
		this.schemaIdentifier = partition.getSchemaIdentifier();
		this.schemaMetaData = ImmutableUtils.makeImmutableNavigableMap(partition.getSchemaMetaData());
		this.content = partition::getContent;
	}

	public BuildScriptScriptInformationEntry(String title, String subTitle, FormattedTextContent content) {
		this.title = Functionals.valSupplier(title);
		this.subTitle = Functionals.valSupplier(subTitle);
		this.content = Functionals.valSupplier(content);
		this.schemaIdentifier = null;
		this.schemaMetaData = Collections.emptyNavigableMap();
	}

	@Override
	public String getTitle() {
		return ObjectUtils.getSupplier(title);
	}

	@Override
	public String getSubTitle() {
		return ObjectUtils.getSupplier(subTitle);
	}

	@Override
	public void setTitle(String title) {
		this.title = Functionals.valSupplier(title);
	}

	@Override
	public void setSubTitle(String subtitle) {
		this.subTitle = Functionals.valSupplier(subtitle);
	}

	@Override
	public void setIconSource(String sourceurl) {
		if (sourceurl == null) {
			this.iconSource = null;
			return;
		}
		if (sourceurl.indexOf('\"') >= 0) {
			throw new IllegalArgumentException("Cannot contain quotes: " + sourceurl);
		}
		this.iconSource = sourceurl;
	}

	@Override
	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	@Override
	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	public String getIconSource() {
		return iconSource;
	}

	public FormattedTextContent getContent() {
		return ObjectUtils.getSupplier(content);
	}
}
