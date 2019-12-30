package saker.build.ide.eclipse.extension.script.proposal;

import java.util.List;
import java.util.Map;

import org.eclipse.swt.graphics.Image;

import saker.build.ide.eclipse.extension.script.information.IScriptInformationEntry;

public interface IScriptProposalEntry {
	public String getDisplayString();

	public String getDisplayType();

	public String getDisplayRelation();

	public void setDisplayString(String display);

	public void setDisplayType(String type);

	public void setDisplayRelation(String relation);

	public void setProposalImage(Image image);

	public List<? extends IScriptInformationEntry> getInformationEntries();

	public String getSchemaIdentifier();

	public Map<String, String> getSchemaMetaData();
}
