package com.rubberjam.eclipse.assistai.preferences.models;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.rubberjam.eclipse.assistai.agent.ChatModelRegistry;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptor;
import com.rubberjam.eclipse.assistai.models.ModelApiDescriptorRepository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;


@Creatable
@Singleton
public class ModelListPreferencePresenter
{

    private final ModelApiDescriptorRepository repository;

    private final ChatModelRegistry chatModelRegistry;

    private ModelListPreferencePage view;

    @Inject
    public ModelListPreferencePresenter(
            ModelApiDescriptorRepository repository,
            ChatModelRegistry chatModelRegistry )
    {
        this.repository = repository;
        this.chatModelRegistry = chatModelRegistry;
    }


    public void addModel()
    {
        view.clearModelSelection();
        view.clearModelDetails();
        view.setDetailsEditable( true );
    }

    public void removeModel( int selectedIndex )
    {
	repository.findByIndex(selectedIndex)
			  .ifPresent(
					  selected -> {
						  chatModelRegistry.invalidate( selected.uid() );
						  repository.delete( selected);
				          view.showModels( repository.listModelApiDescriptors() );
				          view.clearModelDetails();
					  } );
    }

    public void saveModel( int selectedIndex, ModelApiDescriptor updatedModelStub )
    {
        ModelApiDescriptor saved = repository.save( selectedIndex, updatedModelStub );
        chatModelRegistry.invalidate( saved.uid() );
	view.showModels( repository.listModelApiDescriptors() );
        view.clearModelDetails();
    }

    public void setSelectedModel( int selectedIndex )
    {
	repository.findByIndex(selectedIndex)
			  .ifPresentOrElse( selected -> view.showModelDetails(selected),
								() -> view.clearModelDetails() );
    }

    public void registerView( ModelListPreferencePage modelListPreferencePage )
    {
        view = modelListPreferencePage;
        view.showModels( repository.listModelApiDescriptors() );
    }

    public void onPerformDefaults()
    {
        chatModelRegistry.invalidateAll();
        view.showModels( repository.setToDefault() );
        view.clearModelDetails();
    }
}
