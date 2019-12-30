package saker.build.ide.eclipse;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;

public final class BaseAction extends Action {
	public BaseAction(String text) {
		super(text);
	}

	public BaseAction() {
		super();
	}

	public BaseAction(String text, ImageDescriptor image) {
		super(text, image);
	}

	public BaseAction(String text, int style) {
		super(text, style);
	}

}