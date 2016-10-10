/**
 * Copyright (C) 2016 Giancarlo Frison <giancarlo@gfrison.com>
 * <p>
 * Licensed under the UbiCrypt License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://github.com/gfrison/ubicrypt/LICENSE.md
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ubicrypt.ui.ctrl;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import rx.Observable;
import rx.subjects.PublishSubject;
import ubicrypt.core.FileFacade;
import ubicrypt.core.ProgressFile;
import ubicrypt.core.Utils;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.events.SyncBeginEvent;
import ubicrypt.core.events.SynchDoneEvent;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.ProviderDescriptor;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.util.ClassMatcher;
import ubicrypt.core.util.FileInSync;
import ubicrypt.ui.Anchor;
import ubicrypt.ui.tree.FileItem;
import ubicrypt.ui.tree.FolderItem;
import ubicrypt.ui.tree.ITreeItem;
import ubicrypt.ui.tree.ProviderItem;
import ubicrypt.ui.tree.RootFilesItem;
import ubicrypt.ui.tree.RootProvidersItem;
import ubicrypt.ui.tree.TreeCellFactory;
import ubicrypt.ui.tree.TreeFolderItem;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.functions.Actions.empty;
import static ubicrypt.ui.Anchor.emptyPath;
import static ubicrypt.ui.Anchor.searchFile;

public class HomeController implements Initializable, ApplicationContextAware {
    private static final Logger log = getLogger(HomeController.class);
    private final Set<ProgressFile> filesInProgress = ConcurrentHashMap.newKeySet();
    @Inject
    LocalConfig localConfig;
    @Inject
    Stage stage;
    @Inject
    FileFacade fileCommander;
    @Inject
    ProviderCommander providerCommander;
    @Inject
    Anchor ctx;
    @Resource
    List<ProviderDescriptor> providerDescriptors;
    @Resource
    @Qualifier("providerEvent")
    Observable<ProviderEvent> providerEvent;
    @Resource
    PublishSubject<ProgressFile> progressEvents;
    @Resource
    Path basePath;
    @Resource
    Observable<FileEvent> fileEvents;
    @Inject
    FileInSync fileInSync;
    @FXML
    TreeView<ITreeItem> treeView;
    @FXML
    Button addFile;
    @FXML
    Button addProvider;
    @FXML
    Button settings;
    @FXML
    VBox footer;
    @FXML
    Label progressFile;
    @FXML
    Label progressProvider;
    @FXML
    ProgressBar progressBar;
    @Inject
    ProviderLifeCycle providerLifeCycle;
    @FXML
    ProgressIndicator inProgress;
    @FXML
    Label inProgressMessage;
    @Inject
    @Qualifier("appEvents")
    private Observable<Object> appEvents;
    private TreeItem<ITreeItem> filesRoot;
    private final Function<UbiFile, Observable<Boolean>> fileUntracker = file -> fileCommander.removeFile(basePath.resolve(file.getPath()))
            .doOnNext(res -> {
                log.info("untrack file:{}  result:{}", file, res);
                searchFile(filesRoot, file, file.getPath().iterator(), emptyPath).ifPresent(HomeController::removeItem);
            })
            .doOnError(err -> log.error("error untracking file", err));
    private TreeItem<ITreeItem> providersRoot;
    private final Consumer<UbiProvider> providerRemover = provider -> providerCommander.remove(provider).subscribe(res -> {
                log.info("provider:{}, removal result:{}", provider, res);
                if (res) {
                    providersRoot.getChildren().stream()
                            .filter(ti -> ti.getValue().getLabel().equals(provider.providerId()))
                            .findFirst().ifPresent(providersRoot.getChildren()::remove);
                }
            },
            err -> log.error("error on removing provider:{}", provider, err));
    private final Consumer<UbiProvider> providerAdder = provider -> providersRoot.getChildren()
            .add(new TreeItem<>(new ProviderItem(provider, providerDescriptors.stream()
                    .filter(des -> des.getType() == provider.getClass())
                    .findFirst()
                    .map(ProviderDescriptor::getCode)
                    .orElse("default"), providerRemover)));
    private ApplicationContext applicationContext;
    private GeneralProgress gProgress;
    private final Consumer<Path> fileAdder = fromFolder -> {
        FileChooser fc = new FileChooser();
        log.debug("add files from:{}", basePath.resolve(fromFolder));
        fc.setInitialDirectory(basePath.resolve(fromFolder).toFile());
        Optional.ofNullable(fc.showOpenMultipleDialog(stage)).ifPresent(files -> Observable.merge(files.stream().map(file -> {
            log.debug("adding file:{}", file);
            final Path relPath = basePath.relativize(file.toPath());
            return fileCommander.addFile(file.toPath()).flatMap(tupla -> {
                addFiles(relPath.iterator(), basePath, filesRoot, tupla.getT1());
                return tupla.getT2();
            })
                    .doOnNext(result -> log.info("file:{} add result:{}", file, result));
        }).collect(toList()))
                .doOnSubscribe(() -> gProgress.startProgress("Adding Files"))
                .subscribe(empty(), err -> {
                    log.error(err.getMessage(), err);
                    gProgress.stopProgress();
                }, () -> {
                    gProgress.stopProgress();
                }));
    };


    private static void removeItem(final TreeItem<ITreeItem> item) {
        final TreeItem<ITreeItem> parent = item.getParent();
        parent.getChildren().remove(item);
        if (parent.getChildren().isEmpty() && !(parent.getValue() instanceof RootFilesItem)) {
            removeItem(parent);
        }
    }

    @PostConstruct
    public void init() {
        gProgress = new GeneralProgress(inProgress, inProgressMessage);
        treeView.setCellFactory(treeView1 -> new TreeCellFactory(treeView1, fileUntracker, appEvents, gProgress));
        addProvider.setOnMouseClicked(event -> ctx.browse("selectProvider"));
        addFile.setOnMouseClicked(event -> {
            if (!localConfig.getProviders().stream().findAny().isPresent()) {
                ctx.browse("selectProvider");
                return;
            }
            fileAdder.accept(emptyPath);
        });
        settings.setOnMouseClicked(event -> ctx.browse("settings"));
        filesRoot = new TreeItem<>(new RootFilesItem(event -> fileAdder.accept(emptyPath)));
        TreeItem<ITreeItem> root = new TreeItem<>();
        treeView.setRoot(root);

        root.getChildren().add(filesRoot);
        treeView.setShowRoot(false);
        localConfig.getLocalFiles().stream()
                .filter(Utils.trackedFile)
                .forEach(localFile -> addFiles(localFile.getPath().iterator(), basePath, filesRoot, localFile));


        //providers
        providersRoot = new TreeItem<>(new RootProvidersItem());
        root.getChildren().add(providersRoot);
        localConfig.getProviders().stream().forEach(providerAdder);

        //provider status events
        providerEvent.subscribe(pevent -> {
            switch (pevent.getEvent()) {
                case added:
                    log.info("new provider added:{}", pevent.getHook().getProvider());
                    final Optional<TreeItem<ITreeItem>> optItem = providersRoot.getChildren().stream()
                            .filter(item -> ((ProviderItem) item.getValue()).getProvider().equals(pevent.getHook().getProvider()))
                            .findFirst();
                    if (!optItem.isPresent()) {
                        providerAdder.accept(pevent.getHook().getProvider());
                    }
                    pevent.getHook().getStatusEvents().subscribe(event -> {
                        Function<String, String> classLabel;
                        log.info("provider status {}:{}", event, pevent.getHook().getProvider());
                        switch (event) {
                            case error:
                                classLabel = code -> format("tree-provider-%s-error", code);
                                break;
                            default:
                                //TODO:labels for other statuses
                                classLabel = code -> format("tree-provider-%s", code);
                        }
                        optItem.ifPresent(item -> {
                            final ProviderItem providerItem = (ProviderItem) item.getValue();
                            final Node graphics = providerItem.getGraphics();
                            graphics.getStyleClass().clear();
                            providerDescriptors.stream()
                                    .filter(pd -> pd.getType().equals(providerItem.getProvider().getClass()))
                                    .map(ProviderDescriptor::getCode)
                                    .findFirst()
                                    .ifPresent(code -> graphics.getStyleClass().add(classLabel.apply(code)));
                        });
                    });
                    break;
                case removed:
                    //TODO: remove provider
                    break;
                default:
                    log.warn("unmanaged event:{}", pevent.getEvent());
            }
        });


        //remote file events
        fileEvents.filter(fileEvent -> fileEvent.getLocation() == FileEvent.Location.remote)
                .subscribe(fileEvent -> {
                    log.debug("file remote event:{}", fileEvent);
                    //update file icon
                    final UbiFile<UbiFile> file = fileEvent.getFile();
                    Observable.create(fileInSync.call(file))
                            .subscribe(res -> {
                                        searchFile(filesRoot, file).ifPresent(treeView -> {
                                            final Node graphics = treeView.getValue().getGraphics();
                                            graphics.getStyleClass().clear();
                                            graphics.getStyleClass().add(format("tree-file-saved-%s", res));
                                        });
                                    }
                            );
                });
        //local file events
        fileEvents.filter(fileEvent -> fileEvent.getLocation() == FileEvent.Location.local && fileEvent.getType() == FileEvent.Type.created)
                .subscribe(fileEvent -> {
                    log.debug("file local event:{}", fileEvent);
                    localConfig.getLocalFiles().stream()
                            .filter(fileEvent.getFile()::equals)
                            .findFirst()
                            .ifPresent(fe -> addFiles(fileEvent.getFile().getPath().iterator(), basePath, filesRoot, fe));
                    searchFile(filesRoot, fileEvent.getFile()).ifPresent(treeView -> {
                        final Node graphics = treeView.getValue().getGraphics();
                        graphics.getStyleClass().clear();
                        graphics.getStyleClass().add(format("tree-file-saved-%s", true));
                    });
                });

        //file progress monitor
        progressEvents.subscribe(progress -> {
            Platform.runLater(() -> {
                if (progress.isCompleted() || progress.isError()) {
                    log.debug("progress completed");
                    if (!filesInProgress.remove(progress)) {
                        log.warn("progress not tracked. progress file:{}, element:{}", progress.getProvenience().getFile());
                    }
                    Timeline timeline = new Timeline(new KeyFrame(
                            Duration.seconds(2),
                            ae -> {
                                progressFile.setText("");
                                progressProvider.setText("");
                                progressBar.setProgress(0D);
                            }));
                    timeline.play();
                } else {
                    filesInProgress.add(progress);
                }
                if (filesInProgress.isEmpty()) {
//                    footer.setVisible(false);
                    return;
                }
                footer.setVisible(true);
                filesInProgress.stream().findFirst()
                        .ifPresent(pr -> {
                            progressFile.setText(StringUtils.abbreviate(pr.getProvenience().getFile().getPath().getFileName().toString(), 30));
                            progressProvider.setText(StringUtils.abbreviate(pr.getTarget().toString(), 30));
                            progressBar.setProgress((double) progress.getChunk() / pr.getProvenience().getFile().getSize());
                        });
            });
        });


        //sync-done events
        appEvents.subscribe(ClassMatcher.newMatcher()
                .on(SyncBeginEvent.class, event -> {
                    log.info("sync begin received");
                    Platform.runLater(() -> {
                        gProgress.startProgress("synchronizing providers");
                        addFile.setDisable(true);
                        addProvider.setDisable(true);
                    });
                })
                .on(SynchDoneEvent.class, event -> {
                    log.debug("synchronization done");
                    refreshItems(filesRoot);
                    Platform.runLater(() -> {
                        gProgress.stopProgress();
                        addFile.setDisable(false);
                        addProvider.setDisable(false);
                    });
                }));
    }

    private synchronized TreeItem<ITreeItem> addFiles(final Iterator<Path> it, final Path rootPath, final TreeItem<ITreeItem> root, final LocalFile file) {
        if (!it.hasNext()) {
            return root;
        }
        final Path path = it.next();
        final Path resolvedPath = rootPath.resolve(path);
        if (Files.isRegularFile(resolvedPath)) {
            final FileItem item = applicationContext.getBean(FileItem.class, file);
            final TreeItem<ITreeItem> fileItem = new TreeItem<>(item);
            root.getChildren().add(fileItem);
            return fileItem;
        }
        final Optional<TreeItem<ITreeItem>> optTreeItem = root.getChildren().stream().filter(ti -> ((FolderItem) ti.getValue()).getPath().equals(path)).findFirst();
        if (optTreeItem.isPresent()) {
            return addFiles(it, resolvedPath, optTreeItem.get(), file);
        }
        final TreeItem<ITreeItem> fileItem = new TreeFolderItem(new FolderItem(path, event -> fileAdder.accept(resolvedPath)));
        root.getChildren().add(fileItem);
        return addFiles(it, resolvedPath, fileItem, file);
    }

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        Anchor.anchor().getControllerPublisher().onNext(this);
    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;

    }

    private void refreshItems(final TreeItem<ITreeItem> root) {
        root.getChildren().forEach(item -> {
            final ITreeItem value = item.getValue();
            if (value instanceof FileItem) {
                ((FileItem) value).isUp2date();
            }
            refreshItems(item);
        });
    }


}

