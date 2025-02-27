/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.*;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.curse.CurseModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.FloatListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModDownloadListPage extends Control implements DecoratorPage, VersionPage.VersionLoadable {
    protected final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty failed = new SimpleBooleanProperty(false);
    private final boolean versionSelection;
    private final ObjectProperty<Profile.ProfileVersion> version = new SimpleObjectProperty<>();
    private final ListProperty<CurseAddon> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final ModDownloadPage.DownloadCallback callback;
    private boolean searchInitialized = false;

    /**
     * @see org.jackhuang.hmcl.mod.curse.CurseModManager#SECTION_MODPACK
     * @see org.jackhuang.hmcl.mod.curse.CurseModManager#SECTION_MOD
     */
    private final int section;

    public ModDownloadListPage(int section) {
        this(section, null);
    }

    public ModDownloadListPage(int section, ModDownloadPage.DownloadCallback callback) {
        this(section, callback, false);
    }

    public ModDownloadListPage(int section, ModDownloadPage.DownloadCallback callback, boolean versionSelection) {
        this.section = section;
        this.callback = callback;
        this.versionSelection = versionSelection;
    }

    @Override
    public void loadVersion(Profile profile, String version) {
        this.version.set(new Profile.ProfileVersion(profile, version));

        setLoading(false);
        setFailed(false);

        if (!searchInitialized) {
            searchInitialized = true;
            search("", 0, 0, "", 0);
        }
    }

    public boolean isFailed() {
        return failed.get();
    }

    public BooleanProperty failedProperty() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed.set(failed);
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public void search(String userGameVersion, int category, int pageOffset, String searchFilter, int sort) {
        setLoading(true);
        File versionJar = StringUtils.isNotBlank(version.get().getVersion())
                ? version.get().getProfile().getRepository().getVersionJar(version.get().getVersion())
                : null;
        Task.supplyAsync(() -> {
            String gameVersion;
            if (StringUtils.isBlank(version.get().getVersion())) {
                gameVersion = userGameVersion;
            } else {
                gameVersion = GameVersion.minecraftVersion(versionJar).orElse("");
            }
            return gameVersion;
        }).thenApplyAsync(gameVersion -> {
            return CurseModManager.searchPaginated(gameVersion, category, section, pageOffset, searchFilter, sort);
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            setLoading(false);
            if (exception == null) {
                items.setAll(result);
            } else {
                failed.set(true);
            }
        }).start();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModDownloadListPageSkin(this);
    }

    private static class ModDownloadListPageSkin extends SkinBase<ModDownloadListPage> {

        protected ModDownloadListPageSkin(ModDownloadListPage control) {
            super(control);

            BorderPane pane = new BorderPane();

            GridPane searchPane = new GridPane();
            pane.setTop(searchPane);
            searchPane.getStyleClass().addAll("card");
            BorderPane.setMargin(searchPane, new Insets(10, 10, 0, 10));

            ColumnConstraints nameColumn = new ColumnConstraints();
            nameColumn.setMinWidth(USE_PREF_SIZE);
            ColumnConstraints column1 = new ColumnConstraints();
            column1.setHgrow(Priority.ALWAYS);
            ColumnConstraints column2 = new ColumnConstraints();
            column2.setHgrow(Priority.ALWAYS);
            searchPane.getColumnConstraints().setAll(nameColumn, column1, nameColumn, column2);

            searchPane.setHgap(16);
            searchPane.setVgap(10);

            {
                int rowIndex = 0;

                if (control.versionSelection) {
                    JFXComboBox<String> versionsComboBox = new JFXComboBox<>();
                    GridPane.setColumnSpan(versionsComboBox, 3);
                    versionsComboBox.setMaxWidth(Double.MAX_VALUE);

                    searchPane.addRow(rowIndex++, new Label(i18n("version")), versionsComboBox);
                }

                JFXTextField nameField = new JFXTextField();
                nameField.setPromptText(i18n("mods.name"));

                JFXTextField gameVersionField = new JFXTextField();
                Label lblGameVersion = new Label(i18n("world.game_version"));
                gameVersionField.setPromptText(i18n("world.game_version"));
                searchPane.addRow(rowIndex++, new Label(i18n("mods.name")), nameField, lblGameVersion, gameVersionField);

                ObjectBinding<Boolean> hasVersion = BindingMapping.of(getSkinnable().version)
                        .map(version -> version.getVersion() == null);
                lblGameVersion.managedProperty().bind(hasVersion);
                lblGameVersion.visibleProperty().bind(hasVersion);
                gameVersionField.managedProperty().bind(hasVersion);
                gameVersionField.visibleProperty().bind(hasVersion);

                FXUtils.onChangeAndOperate(getSkinnable().version, version -> {
                    if (StringUtils.isNotBlank(version.getVersion())) {
                        GridPane.setColumnSpan(nameField, 3);
                    } else {
                        GridPane.setColumnSpan(nameField, 1);
                    }
                });

                StackPane categoryStackPane = new StackPane();
                JFXComboBox<CategoryIndented> categoryComboBox = new JFXComboBox<>();
                categoryComboBox.getItems().setAll(new CategoryIndented(0, 0));
                categoryStackPane.getChildren().setAll(categoryComboBox);
                categoryComboBox.prefWidthProperty().bind(categoryStackPane.widthProperty());
                categoryComboBox.getStyleClass().add("fit-width");
                categoryComboBox.setPromptText(i18n("mods.category"));
                categoryComboBox.getSelectionModel().select(0);
                Task.supplyAsync(() -> CurseModManager.getCategories(getSkinnable().section))
                        .thenAcceptAsync(Schedulers.javafx(), categories -> {
                            List<CategoryIndented> result = new ArrayList<>();
                            result.add(new CategoryIndented(0, 0));
                            for (CurseModManager.Category category : categories) {
                                resolveCategory(category, 0, result);
                            }
                            categoryComboBox.getItems().setAll(result);
                        }).start();

                StackPane sortStackPane = new StackPane();
                JFXComboBox<String> sortComboBox = new JFXComboBox<>();
                sortStackPane.getChildren().setAll(sortComboBox);
                sortComboBox.prefWidthProperty().bind(sortStackPane.widthProperty());
                sortComboBox.getStyleClass().add("fit-width");
                sortComboBox.setPromptText(i18n("search.sort"));
                sortComboBox.getItems().setAll(
                        i18n("curse.sort.date_created"),
                        i18n("curse.sort.popularity"),
                        i18n("curse.sort.last_updated"),
                        i18n("curse.sort.name"),
                        i18n("curse.sort.author"),
                        i18n("curse.sort.total_downloads"));
                sortComboBox.getSelectionModel().select(0);
                searchPane.addRow(rowIndex++, new Label(i18n("mods.category")), categoryStackPane, new Label(i18n("search.sort")), sortStackPane);

                JFXButton searchButton = new JFXButton();
                searchButton.setText(i18n("search"));
                GridPane.setHalignment(searchButton, HPos.LEFT);
                searchPane.addRow(rowIndex++, searchButton);

                EventHandler<ActionEvent> searchAction = e -> getSkinnable()
                        .search(gameVersionField.getText(),
                                Optional.ofNullable(categoryComboBox.getSelectionModel().getSelectedItem())
                                        .map(CategoryIndented::getCategoryId)
                                        .orElse(0),
                                0,
                                nameField.getText(),
                                sortComboBox.getSelectionModel().getSelectedIndex());
                searchButton.setOnAction(searchAction);
                nameField.setOnAction(searchAction);
                gameVersionField.setOnAction(searchAction);
                categoryComboBox.setOnAction(searchAction);
                sortComboBox.setOnAction(searchAction);
            }

            SpinnerPane spinnerPane = new SpinnerPane();
            pane.setCenter(spinnerPane);
            {
                spinnerPane.loadingProperty().bind(getSkinnable().loadingProperty());
                spinnerPane.failedReasonProperty().bind(Bindings.createStringBinding(() -> {
                    if (getSkinnable().isFailed()) {
                        return i18n("download.failed.refresh");
                    } else {
                        return null;
                    }
                }, getSkinnable().failedProperty()));

                JFXListView<CurseAddon> listView = new JFXListView<>();
                spinnerPane.setContent(listView);
                Bindings.bindContent(listView.getItems(), getSkinnable().items);
                listView.setOnMouseClicked(e -> {
                    if (listView.getSelectionModel().getSelectedIndex() < 0)
                        return;
                    CurseAddon selectedItem = listView.getSelectionModel().getSelectedItem();
                    Controllers.navigate(new ModDownloadPage(selectedItem, getSkinnable().version.get(), getSkinnable().callback));
                });
                listView.setCellFactory(x -> new FloatListCell<CurseAddon>(listView) {
                    TwoLineListItem content = new TwoLineListItem();
                    ImageView imageView = new ImageView();

                    {
                        HBox container = new HBox(8);
                        container.setAlignment(Pos.CENTER_LEFT);
                        pane.getChildren().add(container);

                        container.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), content);
                    }

                    @Override
                    protected void updateControl(CurseAddon dataItem, boolean empty) {
                        if (empty) return;
                        content.setTitle(dataItem.getName());
                        content.setSubtitle(dataItem.getSummary());
                        content.getTags().setAll(dataItem.getCategories().stream()
                                .map(category -> i18n("curse.category." + category.getCategoryId()))
                                .collect(Collectors.toList()));

                        for (CurseAddon.Attachment attachment : dataItem.getAttachments()) {
                            if (attachment.isDefault()) {
                                imageView.setImage(new Image(attachment.getThumbnailUrl(), 40, 40, true, true, true));
                            }
                        }
                    }
                });
            }

            getChildren().setAll(pane);
        }

        private static class CategoryIndented {
            private final int indent;
            private final int categoryId;

            public CategoryIndented(int indent, int categoryId) {
                this.indent = indent;
                this.categoryId = categoryId;
            }

            public int getIndent() {
                return indent;
            }

            public int getCategoryId() {
                return categoryId;
            }

            @Override
            public String toString() {
                return StringUtils.repeats(' ', indent) + i18n("curse.category." + categoryId);
            }
        }

        private static void resolveCategory(CurseModManager.Category category, int indent, List<CategoryIndented> result) {
            result.add(new CategoryIndented(indent, category.getId()));
            for (CurseModManager.Category subcategory : category.getSubcategories()) {
                resolveCategory(subcategory, indent + 1, result);
            }
        }
    }
}
