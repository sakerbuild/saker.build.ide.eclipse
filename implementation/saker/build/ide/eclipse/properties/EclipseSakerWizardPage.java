/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.ide.eclipse.properties;

import org.eclipse.jface.dialogs.DialogPage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

public abstract class EclipseSakerWizardPage extends DialogPage implements IWizardPage {
	private IWizard wizard = null;

	private IWizardPage previousPage = null;

	protected EclipseSakerWizardPage() {
		this(null, (ImageDescriptor) null);
	}

	protected EclipseSakerWizardPage(String title, ImageDescriptor titleImage) {
		super(title, titleImage);
	}

	@Override
	public boolean canFlipToNextPage() {
		return isPageComplete() && getNextPage() != null;
	}

	protected final IWizardContainer getContainer() {
		if (wizard == null) {
			return null;
		}
		return wizard.getContainer();
	}

	@Override
	public Image getImage() {
		Image result = super.getImage();

		if (result == null && wizard != null) {
			return wizard.getDefaultPageImage();
		}

		return result;
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	@Override
	public abstract IWizardPage getNextPage();

	@Override
	public IWizardPage getPreviousPage() {
		return previousPage;
	}

	@Override
	public Shell getShell() {
		IWizardContainer container = getContainer();
		if (container == null) {
			return null;
		}

		// Ask the wizard since our contents may not have been created.
		return container.getShell();
	}

	@Override
	public IWizard getWizard() {
		return wizard;
	}

	/**
	 * Returns whether this page is the current one in the wizard's container.
	 *
	 * @return <code>true</code> if the page is active, and <code>false</code> otherwise
	 */
	protected final boolean isCurrentPage() {
		return (getContainer() != null && this == getContainer().getCurrentPage());
	}

	@Override
	public boolean isPageComplete() {
		return true;
	}

	@Override
	public void setDescription(String description) {
		super.setDescription(description);
		if (isCurrentPage()) {
			getContainer().updateTitleBar();
		}
	}

	@Override
	public void setErrorMessage(String newMessage) {
		super.setErrorMessage(newMessage);
		if (isCurrentPage()) {
			getContainer().updateMessage();
		}
	}

	@Override
	public void setImageDescriptor(ImageDescriptor image) {
		super.setImageDescriptor(image);
		if (isCurrentPage()) {
			getContainer().updateTitleBar();
		}
	}

	@Override
	public void setMessage(String newMessage, int newType) {
		super.setMessage(newMessage, newType);
		if (isCurrentPage()) {
			getContainer().updateMessage();
		}
	}

	@Override
	public void setPreviousPage(IWizardPage page) {
		previousPage = page;
	}

	@Override
	public void setTitle(String title) {
		super.setTitle(title);
		if (isCurrentPage()) {
			getContainer().updateTitleBar();
		}
	}

	@Override
	public void setWizard(IWizard newWizard) {
		wizard = newWizard;
	}

	@Override
	public String toString() {
		return getName();
	}

}
