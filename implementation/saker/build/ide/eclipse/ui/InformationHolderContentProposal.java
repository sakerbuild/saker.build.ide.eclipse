package saker.build.ide.eclipse.ui;

import org.eclipse.jface.fieldassist.ContentProposal;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.InformationHolder;

public final class InformationHolderContentProposal extends ContentProposal {
	private final InformationHolder info;

	public InformationHolderContentProposal(String content, InformationHolder targetinfo) {
		super(content);
		this.info = targetinfo;
	}

	@Override
	public String getDescription() {
		FormattedTextContent info = this.info.getInformation();
		if (info == null) {
			return null;
		}
		return info.getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT);
	}
}