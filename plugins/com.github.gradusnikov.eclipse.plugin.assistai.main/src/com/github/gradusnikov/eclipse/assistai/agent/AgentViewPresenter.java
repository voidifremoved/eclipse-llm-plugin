package com.github.gradusnikov.eclipse.assistai.agent;

import java.util.List;
import java.util.UUID;

import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.resources.IProject;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchPage;

import com.github.gradusnikov.eclipse.assistai.chat.Attachment;
import com.github.gradusnikov.eclipse.assistai.chat.ChatMessage;
import com.github.gradusnikov.eclipse.assistai.prompt.PromptRepository;
import com.github.gradusnikov.eclipse.assistai.resources.IResourceCacheListener;
import com.github.gradusnikov.eclipse.assistai.resources.ResourceCacheEvent;
import com.github.gradusnikov.eclipse.assistai.view.ChatView;
import com.github.gradusnikov.eclipse.assistai.view.PartAccessor;
import com.github.gradusnikov.eclipse.assistai.models.ModelApiDescriptorRepository;
import com.github.gradusnikov.eclipse.assistai.view.ApplyPatchWizardHelper;
import com.github.gradusnikov.eclipse.assistai.mcp.services.CodeEditingService;
import com.github.gradusnikov.eclipse.assistai.tools.ResourceUtilities;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.scheduler.Schedulers;

@Creatable
@Singleton
@SuppressWarnings("restriction")
public class AgentViewPresenter implements IResourceCacheListener
{
    @Inject private AgentSessionManager sessionManager;
    @Inject private PartAccessor partAccessor;
    @Inject private PromptRepository promptRepository;
    @Inject private ModelApiDescriptorRepository modelRepository;
    @Inject private ILog logger;
    @Inject private CodeEditingService codeEditingService;
    @Inject private ApplyPatchWizardHelper applyPatchWizardHelper;

    // We use Object to avoid OSGi visibility errors for reactor.core.Disposable
    private Object currentStream;

    public void onSendUserMessage(String text)
    {
        onSendUserMessage(text, null);
    }

    public void onSendUserMessage(String text, List<Attachment> attachments)
    {
        AgentSession session = sessionManager.getOrCreateSession();

        // Handle slash commands mapping manually, similar to ChatViewPresenter
        if (text.startsWith("/")) {
            String[] parts = text.split(" ", 2);
            String command = parts[0].substring(1);
            String rest = parts.length > 1 ? parts[1] : "";
            try {
                String template = promptRepository.getPrompt(command);
                text = template.replace("${userMessage}", rest);
            } catch (Exception e) {
                // Ignore missing prompt
            }
        }

        String userText = text;
        String userMessageId = UUID.randomUUID().toString();

        applyToView(view -> {
            view.clearUserInput();
            view.appendMessage(userMessageId, "user");
            view.setMessageHtml(userMessageId, userText);
            view.setInputEnabled(false);
        });

        String assistantMessageId = UUID.randomUUID().toString();
        applyToView(view -> view.appendMessage(assistantMessageId, "assistant"));

        StringBuilder accumulatedResponse = new StringBuilder();

        currentStream = session.sendMessage(userText, attachments)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                chatResponse -> {
                    String content = chatResponse.getResult().getOutput().getText();
                    if (content != null) {
                        accumulatedResponse.append(content);
                        String currentHtml = accumulatedResponse.toString();
                        applyToView(view -> view.setMessageHtml(assistantMessageId, currentHtml));
                    }
                },
                error -> {
                    applyToView(view -> {
                        view.setMessageHtml(assistantMessageId, "Error: " + error.getMessage());
                        view.setInputEnabled(true);
                    });
                },
                () -> {
                    // Stream finished, update conversation history in session
                    session.appendAssistantResponse(accumulatedResponse.toString());
                    applyToView(view -> view.setInputEnabled(true));
                }
            );
    }

    public void onStop()
    {
        if (currentStream != null)
        {
            try {
                java.lang.reflect.Method isDisposedMethod = currentStream.getClass().getMethod("isDisposed");
                boolean isDisposed = (Boolean) isDisposedMethod.invoke(currentStream);
                if (!isDisposed) {
                    java.lang.reflect.Method disposeMethod = currentStream.getClass().getMethod("dispose");
                    disposeMethod.invoke(currentStream);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        applyToView(view -> view.setInputEnabled(true));
    }

    public void onChatModelSelected(String modelId)
    {
        sessionManager.switchModel(modelId);
        modelRepository.setChatModelInUse(modelId);
    }

    public void onClear()
    {
        sessionManager.destroySession();
        sessionManager.newSession();
        applyToView(ChatView::clearChatView);
    }

    @Override
    public void cacheChanged(ResourceCacheEvent event) {
        // Ignored for now
    }

    private void applyToView(java.util.function.Consumer<ChatView> action)
    {
        partAccessor.findMessageView().ifPresent(view -> {
            Display.getDefault().asyncExec(() -> action.accept(view));
        });
    }

    public void onSendPredefinedPrompt(com.github.gradusnikov.eclipse.assistai.prompt.Prompts type, ChatMessage message) {
        onSendUserMessage(message.getContent(), message.getAttachments());
    }

    public void onViewVisible() {
        // Ignored
    }

    public void onRemoveAttachment(int index) {
        // Ignored
    }

    public void onAddAttachment() {
        // Ignored
    }

    public void onReplayLastMessage() {
        // Ignored
    }

    public void onAttachmentAdded(ImageData imageData) {
        // Ignored
    }

    public void onCopyCode(String codeBlock) {
        // We use the clipboard to copy code block
        Display.getDefault().asyncExec(() -> {
            org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
            org.eclipse.swt.dnd.TextTransfer textTransfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
            clipboard.setContents(new Object[] { codeBlock }, new org.eclipse.swt.dnd.Transfer[] { textTransfer });
            clipboard.dispose();
        });
    }

    public void onApplyPatch(String codeBlock) {
        Display.getDefault().asyncExec(() -> {
            Optional.ofNullable(PlatformUI.getWorkbench())
                    .map(workbench -> workbench.getActiveWorkbenchWindow())
                    .map(window -> window.getActivePage())
                    .map(page -> page.getActiveEditor())
                    .flatMap(editor -> Optional.ofNullable(editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class)))
                    .ifPresent(textEditor -> {
                        if (textEditor.getEditorInput() instanceof org.eclipse.ui.part.FileEditorInput) {
                            org.eclipse.ui.part.FileEditorInput fileInput = (org.eclipse.ui.part.FileEditorInput) textEditor.getEditorInput();
                            String projectName = fileInput.getFile().getProject().getName();
                            applyPatchWizardHelper.showApplyPatchWizardDialog(codeBlock, projectName);
                        }
                    });
        });
    }

    public void onInsertCode(String codeBlock) {
        Display.getDefault().asyncExec(() -> {
            Optional.ofNullable(PlatformUI.getWorkbench())
                    .map(workbench -> workbench.getActiveWorkbenchWindow())
                    .map(window -> window.getActivePage())
                    .map(page -> page.getActiveEditor())
                    .flatMap(editor -> Optional.ofNullable(editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class)))
                    .ifPresent(textEditor -> {
                        var selectionProvider = textEditor.getSelectionProvider();
                        var document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
                        if (selectionProvider != null && document != null) {
                            var selection = (org.eclipse.jface.text.ITextSelection) selectionProvider.getSelection();
                            try {
                                if (selection.getLength() > 0) {
                                    document.replace(selection.getOffset(), selection.getLength(), codeBlock);
                                } else {
                                    document.replace(selection.getOffset(), 0, codeBlock);
                                }
                            } catch (org.eclipse.jface.text.BadLocationException e) {
                                logger.error("Error inserting code at location", e);
                            }
                        }
                    });
        });
    }

    public void onDiffCode(String codeBlock) {
        Display.getDefault().asyncExec(() -> {
            Optional.ofNullable(PlatformUI.getWorkbench())
                    .map(workbench -> workbench.getActiveWorkbenchWindow())
                    .map(window -> window.getActivePage())
                    .map(page -> page.getActiveEditor())
                    .flatMap(editor -> Optional.ofNullable(editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class)))
                    .ifPresent(textEditor -> {
                        if (textEditor.getEditorInput() instanceof org.eclipse.ui.part.FileEditorInput) {
                            org.eclipse.ui.part.FileEditorInput fileInput = (org.eclipse.ui.part.FileEditorInput) textEditor.getEditorInput();
                            String projectName = fileInput.getFile().getProject().getName();
                            String filePath = fileInput.getFile().getProjectRelativePath().toString();
                            String diff = codeEditingService.generateCodeDiff(projectName, filePath, codeBlock, 3);
                            if (diff != null && !diff.isBlank()) {
                                applyPatchWizardHelper.showApplyPatchWizardDialog(diff, projectName);
                            }
                        }
                    });
        });
    }

    public void onNewFile(String codeBlock, String lang) {
        Display.getDefault().asyncExec(() -> {
            IProject project = Optional.ofNullable(PlatformUI.getWorkbench())
                    .map(IWorkbench::getActiveWorkbenchWindow)
                    .map(IWorkbenchWindow::getActivePage)
                    .map(IWorkbenchPage::getActiveEditor)
                    .map(editor -> editor.getEditorInput())
                    .filter(input -> input instanceof org.eclipse.ui.part.FileEditorInput)
                    .map(input -> ((org.eclipse.ui.part.FileEditorInput) input).getFile().getProject())
                    .orElse(null);
            if (project != null) {
                String suggestedFileName = ResourceUtilities.getSuggestedFileName(lang, codeBlock);
                IPath suggestedPath = ResourceUtilities.getSuggestedPath(project, lang, codeBlock);
                org.eclipse.ui.dialogs.WizardNewFileCreationPage newFilePage = new org.eclipse.ui.dialogs.WizardNewFileCreationPage("NewFilePage", new org.eclipse.jface.viewers.StructuredSelection(project));
                newFilePage.setTitle("New File");
                newFilePage.setDescription(String.format("Create a new %s file in the project", ResourceUtilities.getFileExtensionForLang(lang)));
                if (suggestedPath != null) {
                    newFilePage.setContainerFullPath(suggestedPath);
                }
                newFilePage.setFileName(suggestedFileName);
                org.eclipse.jface.wizard.Wizard wizard = new org.eclipse.jface.wizard.Wizard() {
                    @Override
                    public boolean performFinish() {
                        return true;
                    }
                };
                wizard.addPage(newFilePage);
                org.eclipse.jface.wizard.WizardDialog dialog = new org.eclipse.jface.wizard.WizardDialog(Display.getDefault().getActiveShell(), wizard);
                dialog.open();
            }
        });
    }

    public void onRemoveMessage(String messageId) {
        // Ignored
    }
}
