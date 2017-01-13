/* 
 * Copyright 2016 Andrew Burch.
 *
 * This software is not availabe for distribution under any license.
 */
package studenttracker;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import static javafx.scene.control.OverrunStyle.LEADING_ELLIPSIS;
import javafx.scene.input.MouseButton;
import static javafx.scene.paint.Color.rgb;

/**
 *
 * @author Andrew Burch
 */
public class StudentTracker extends Application {

    private static final String userPath = System.getProperty("user.dir");
    private static final String resourcePath = userPath + File.separator + "Student Tracker";
    private static String schoolPath;
    private static String studentFilesPath;
    private static final String dbName = "students";
    private static List<String[]> backgroundList = new ArrayList<>();

    enum Category {
        fullName, firstName, lastName, indStart, indEnd, groupStart, groupEnd,
        checkInStart, checkInEnd, walkIns, forms, notes, hasIEP, has504, hasEval
    }

    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override

    public void start(Stage primaryStage) {
        //Set class variables excluding selectedStudent.

        new File(resourcePath).mkdir();
        Properties globalProperties = new Properties();
        establishGlobalProperties(globalProperties);

        schoolPath = resourcePath + File.separator + globalProperties
                .getProperty("selectedSchool");
        new File(schoolPath).mkdir();
        studentFilesPath = schoolPath + File.separator + "Student Files";
        new File(studentFilesPath).mkdir();
        final Properties localProperties = new Properties();
        establishLocalProperties(localProperties);
        checkDatabase();
        final List<String> studentList = new ArrayList<>();
        final String listPath = localProperties.getProperty("listPath");
        if (!listPath.isEmpty()) {
            readStudentList(studentList, listPath);
        }
        final ObservableList<String> observableStudentList
                = FXCollections.observableList(studentList);
        final StringProperty listStatusTextProperty = new SimpleStringProperty();
        final Label schoolLabel = new Label("Active school:");
        final Label activeSchool = new Label();
        final StringProperty activeSchoolTextProperty = new SimpleStringProperty();
        activeSchoolTextProperty.setValue(globalProperties
                .getProperty("selectedSchool"));
        activeSchool.textProperty().bind(activeSchoolTextProperty);
        final Label listStatusText = createStatusText(listStatusTextProperty);
        setListStatusTextProperty(listPath, listStatusTextProperty);
        final Label fieldLabel = new Label("Student Name:");
        final TextField studentField = new TextField();
        final ListView<String> visibleStudentList = createVisibleStudentList(
                observableStudentList, studentField);
        final Button setDirBtn = initDirectoryButton(visibleStudentList,
                observableStudentList, studentList, studentField,
                listStatusText, localProperties, listStatusTextProperty,
                primaryStage);
        final Label listLabel = new Label("Student List:");
        final Button viewIndButton = initButton("ViewIndividualListButton.png",
                110, 66);
        viewIndButton.setOnMouseClicked((MouseEvent event) -> {
            List<String> indList = collectCurrentEnrollment(Category.indStart,
                    Category.indEnd);
            initClientStage(indList, "Individual");
        });
        final Button viewGroupButton = initButton("ViewGroupListButton.png",
                110, 66);
        viewGroupButton.setOnMouseClicked((MouseEvent event) -> {
            List<String> groupList = collectCurrentEnrollment(Category.groupStart,
                    Category.groupEnd);
            initClientStage(groupList, "Group");
        });
        final Button viewArchiveButton = initViewArchiveButton(visibleStudentList,
                observableStudentList, studentList, listStatusTextProperty,
                localProperties, studentField);
        final Button archiveStudentButton
                = initButton("ArchiveStudentButton.png", 110, 66);
        archiveStudentButton.setOnAction((ActionEvent event) -> {
            if (visibleStudentList.getSelectionModel().getSelectedItem() != null) {
                try {
                    confirmationAlert("Archive");
                    final String selectedStudent = visibleStudentList.
                            getSelectionModel().getSelectedItem().trim();
                    try {
                        checkStudent(selectedStudent);
                        transferTableEntry(selectedStudent, "archive", "clients");
                        removeStudentFromList(studentList, selectedStudent,
                                localProperties.getProperty("listPath"));
                        setFilteredList(visibleStudentList, observableStudentList,
                                studentField);

                        final String[] fileName = new File(
                                localProperties.getProperty("listPath")).getName()
                                .split(" ");
                        if (!fileName[0].equals("Modified")) {
                            localProperties.setProperty("listPath", resourcePath
                                    + File.separator
                                    + "Modified " + new File(
                                            localProperties.getProperty("listPath"))
                                    .getName());
                            storeLocalProperties(localProperties);
                            setListStatusTextProperty(localProperties
                                    .getProperty("listPath"),
                                    listStatusTextProperty);
                        }
                    } catch (SQLException sql) {
                        errorAlert("No Student", "");
                    }

                } catch (IllegalArgumentException ia) {
                }
            }
        });
        final Button addStudentButton = initButton("AddStudentButton.png", 110,
                66);
        addStudentButton.setOnAction((ActionEvent event) -> {
            initAddStudentStage(visibleStudentList, observableStudentList,
                    studentList, studentField, listStatusTextProperty,
                    localProperties);
        });
        final Button removeStudentButton = initRemoveStudentButton(
                localProperties, listStatusTextProperty, visibleStudentList,
                observableStudentList, studentList, studentField);
        final MenuBar menuBar = createMenu(visibleStudentList,
                observableStudentList, studentList, activeSchoolTextProperty,
                listStatusTextProperty, globalProperties, localProperties,
                primaryStage, listStatusText, studentField);
        final Scene scene = new Scene(primaryStageLayout(schoolLabel,
                activeSchool, listStatusText, fieldLabel, studentField,
                visibleStudentList, setDirBtn, listLabel, viewIndButton,
                viewGroupButton, viewArchiveButton, addStudentButton,
                removeStudentButton, archiveStudentButton, menuBar));
        scene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        primaryStage.setTitle("Student Tracker");
        primaryStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        primaryStage.setResizable(false);
        primaryStage.setScene(scene);
        primaryStage.show();
        if (listPath.isEmpty()) {
            listStatusText.setTextFill(rgb(255, 0, 0));
        }
    }

    private Button initButton(String imageFile, int width, int height) {
        Button button = new Button();
        Image buttonImage = new Image("/studenttracker/Icons/" + imageFile,
                width, height, true, true);
        button.setGraphic(new ImageView(buttonImage));
        return button;
    }

    private Button initViewArchiveButton(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty listStatusTextProperty,
            Properties localProperties, TextField studentField) {
        Button viewArchiveButton = initButton("ViewArchiveButton.png", 110, 66);
        viewArchiveButton.setOnAction((ActionEvent event) -> {
            initArchiveStage(visibleStudentList, observableStudentList,
                    studentList, listStatusTextProperty, localProperties,
                    studentField);

        });
        return viewArchiveButton;
    }

    private Button initAddFormButton(Student student, Stage parentStage,
            List forms, ListView visibleList) {
        Button addFormButton = initButton("AddFormButton.png", 110, 66);
        addFormButton.setOnAction((ActionEvent event) -> {
            String studentPath = studentFilesPath + File.separator
                    + student.getFullName();
            FileChooser chooseList = new FileChooser();
            chooseList.setInitialDirectory(new File(studentPath));
            chooseList.setTitle("Select Form for " + student.getFullName());
            File formFile = chooseList.showOpenDialog(parentStage);
            if (formFile != null) {
                String fileName = formFile.getName();
                try {
                    copy(formFile, new File(studentPath + File.separator
                            + fileName));
                    student.addForms(fileName);
                    forms.add(fileName);
                    modifyDatabase(Category.forms, student);
                    visibleList.setItems(FXCollections.observableList(forms));
                } catch (IllegalArgumentException ia) {
                }
            }
        });
        return addFormButton;
    }

    private Button initRemoveFormButton(Student student, Stage parentStage,
            List forms, ListView visibleList) {
        Button removeFormButton = initButton("RemoveFormButton.png", 110, 66);
        removeFormButton.setOnAction((ActionEvent event) -> {
            if (!forms.isEmpty()) {
                String fileName = (String) visibleList.getSelectionModel()
                        .getSelectedItem();
                if (fileName != null) {
                    File removeFile = new File(studentFilesPath + File.separator
                            + student.getFullName() + File.separator
                            + fileName);
                    removeFile.delete();
                    forms.remove(fileName);
                    student.removeForm(fileName);
                    modifyDatabase(Category.forms, student);
                    visibleList.setItems(FXCollections.observableList(forms));
                }
            }
        });
        return removeFormButton;
    }

    private Button createAddWalkInButton(Student student) {
        Button addWalkInButton = initButton("AddWalkInButton.png", 110, 66);
        addWalkInButton.setOnAction((final ActionEvent addWalkAct) -> {
            String walkInDate = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("MM-dd-yyyy"));
            TextInputDialog addWalkInDialog = new TextInputDialog(walkInDate);
            DialogPane dialogPane = addWalkInDialog.getDialogPane();
            dialogPane.getStylesheets().add(getClass().getResource("Main.css")
                    .toExternalForm());
            Button okButton = (Button) addWalkInDialog.getDialogPane()
                    .lookupButton(ButtonType.OK);
            Image okImage = new Image("/studenttracker/Icons/OKButton.png", 55, 33,
                    true,
                    true);
            okButton.setGraphic(new ImageView(okImage));
            okButton.setText(null);
            Button cancelButton = (Button) addWalkInDialog.getDialogPane()
                    .lookupButton(ButtonType.CANCEL);
            Image cancelImage = new Image(
                    "/studenttracker/Icons/CancelButton.png", 55, 33,
                    true, true);
            cancelButton.setGraphic(new ImageView(cancelImage));
            cancelButton.setText(null);
            addWalkInDialog.setContentText("Add this date to "
                    + student.getFullName() + "'s walk-ins?");
            Optional<String> walkInResult = addWalkInDialog.showAndWait();
            if (walkInResult.isPresent()) {
                String walkIn = walkInResult.get();
                if (!walkIn.isEmpty()) {
                    student.addWalkIn(walkIn);
                    modifyDatabase(Category.walkIns, student);
                }

            }
        });
        return addWalkInButton;
    }

    private Button createDetailsButton(Student student) {
        Button detailsButton = initButton("DetailsButton.png", 110, 66);
        detailsButton.setOnAction((ActionEvent event) -> {
            initDetailsStage(student);
        });
        return detailsButton;
    }

    private Button initPrintButton(List<String> printList, String group) {
        Button printButton = initButton("PrintListButton.png", 110, 66);
        printButton.setOnAction((ActionEvent event) -> {
            String fileDate = LocalDateTime.now().format(DateTimeFormatter
                    .ofPattern("yyyy-MM-dd"));
            String printDate = LocalDateTime.now().format(DateTimeFormatter
                    .ofPattern("MMM d, yyyy"));
            String printTime = LocalDateTime.now().format(DateTimeFormatter
                    .ofPattern("h:mm a"));
            String printFile = schoolPath + File.separator + "Printed Lists"
                    + File.separator + fileDate + " " + group + " List.txt";
            String header = group + " counseling client list created "
                    + printDate + " at " + printTime + ":";
            print(printList, printFile, header);
        });
        return printButton;
    }

    private Button initRemoveStudentButton(Properties localProperties,
            StringProperty listStatusTextProperty,
            ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, TextField studentField) {
        Button removeStudentButton = initButton("RemoveStudentButton.png", 110,
                66);
        removeStudentButton.setOnAction((ActionEvent event) -> {
            String drop
                    = visibleStudentList.getSelectionModel().getSelectedItem();
            if (drop != null) {
                String[] fileName = new File(localProperties
                        .getProperty("listPath")).getName().split(" ");

                if (!fileName[0].equals("Modified")) {
                    localProperties.setProperty("listPath", schoolPath
                            + File.separator + "Modified " + new File(
                                    localProperties.getProperty("listPath"))
                            .getName());
                    storeLocalProperties(localProperties);
                    setListStatusTextProperty(localProperties
                            .getProperty("listPath"), listStatusTextProperty);
                }
                removeStudentFromList(studentList, drop,
                        localProperties.getProperty("listPath"));
                setFilteredList(visibleStudentList, observableStudentList,
                        studentField);
            }
        });
        return removeStudentButton;
    }

    private Button initDirectoryButton(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, TextField studentField,
            Label listStatusText, Properties localProperties,
            StringProperty listStatusTextProperty, Stage primaryStage) {
        //Create button to choose student list directory.

        Button dirBtn = initButton("SetListButton.png", 110, 66);
        dirBtn.setId("dirBtn");
        FileChooser chooseList = new FileChooser();
        chooseList.setInitialDirectory(new File(resourcePath));
        chooseList.setTitle("Select Student List File");
        dirBtn.setOnAction((final ActionEvent event) -> {
            File listFile = chooseList.showOpenDialog(primaryStage);
            if (listFile != null) {
                File target = new File(schoolPath + File.separator
                        + listFile.getName());
                try {
                    copy(listFile, target);
                } catch (IllegalArgumentException ia) {
                }
                localProperties.setProperty("listPath", target.toString());
                storeLocalProperties(localProperties);
                readStudentList(studentList, localProperties
                        .getProperty("listPath"));
                setFilteredList(visibleStudentList, observableStudentList,
                        studentField);
                setListStatusTextProperty(localProperties
                        .getProperty("listPath"), listStatusTextProperty);
                listStatusText.setTextFill(rgb(255, 255, 255));
            }
        });
        return dirBtn;
    }

    private MenuBar createMenu(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty activeSchoolTextProperty,
            StringProperty listStatusTextProperty, Properties globalProperties,
            Properties localProperties, Stage primaryStage,
            Label listStatusText, TextField studentField) {
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        Menu changeSchool = new Menu("Change School");
        Menu assignDatabase = new Menu("Assign Database to...");
        Menu removeSchool = new Menu("Remove School");
        updateMenus(visibleStudentList, observableStudentList,
                studentList, activeSchoolTextProperty, listStatusTextProperty,
                globalProperties, localProperties, primaryStage, changeSchool,
                removeSchool, assignDatabase, listStatusText, studentField);
        MenuItem addSchool = buildAddSchoolMenu(visibleStudentList,
                observableStudentList, studentList, activeSchoolTextProperty,
                listStatusTextProperty, globalProperties, localProperties,
                primaryStage, changeSchool, removeSchool, assignDatabase,
                listStatusText, studentField);
        MenuItem readMeMenu = buildReadMeMenuItem(primaryStage);
        MenuItem closeItem = buildCloseMenuItem();
        fileMenu.getItems().addAll(changeSchool, addSchool,
                removeSchool, assignDatabase, readMeMenu, closeItem);
        menuBar.getMenus().add(fileMenu);
        return menuBar;
    }

    private MenuItem buildAddSchoolMenu(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty activeSchoolTextProperty,
            StringProperty listStatusTextProperty, Properties globalProperties,
            Properties localProperties, Stage primaryStage, Menu changeSchool,
            Menu removeSchool, Menu assignDatabase, Label listStatusText,
            TextField studentField) {
        MenuItem addSchool = new MenuItem("Add School");
        addSchool.setOnAction((ActionEvent event) -> {
            initAddSchoolStage(visibleStudentList, observableStudentList,
                    studentList, activeSchoolTextProperty,
                    listStatusTextProperty, globalProperties, localProperties,
                    primaryStage, changeSchool, removeSchool, assignDatabase,
                    listStatusText, studentField);
        });
        return addSchool;
    }

    private MenuItem buildReadMeMenuItem(Stage primaryStage) {
        //Create menu item for readme.

        MenuItem readMeMenu = new MenuItem("ReadMe");
        readMeMenu.setOnAction((ActionEvent event) -> {
            final Stage readMeStage = new Stage();
            readMeStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
            readMeStage.initModality(Modality.NONE);
            readMeStage.initOwner(primaryStage);
            readMeStage.setTitle("ReadMe");
            VBox dialogVbox = new VBox(20);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    getClass().getResourceAsStream("readme.txt")));
            StringBuilder readMeLines = new StringBuilder();
            try {
                getReadMeText(readMeLines, reader);
                reader.close();
            } catch (IOException io) {
                errorAlert("File Not Found", getTrace(io));
            }
            Text readMeContent = new Text(readMeLines.toString());
            readMeContent.setWrappingWidth(400);
            ScrollPane readMePane = new ScrollPane();
            readMePane.setContent(readMeContent);
            dialogVbox.getChildren().add(readMePane);
            Scene dialogScene = new Scene(dialogVbox, 420, 420);
            readMeStage.setScene(dialogScene);
            readMeStage.show();
        });
        return readMeMenu;
    }

    private MenuItem buildCloseMenuItem() {
        //Create menu item to exit application.

        MenuItem closeMenu = new MenuItem("Close");
        closeMenu.setOnAction((ActionEvent event) -> {
            System.exit(0);
        });
        return closeMenu;
    }

    private void updateMenus(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty activeSchoolTextProperty,
            StringProperty listStatusTextProperty, Properties globalProperties,
            Properties localProperties, Stage primaryStage, Menu changeSchool,
            Menu removeSchool, Menu assignDatabase, Label listStatusText,
            TextField studentField) {
        changeSchool.getItems().clear();
        removeSchool.getItems().clear();
        assignDatabase.getItems().clear();
        List<String> schools = Arrays.asList(globalProperties
                .getProperty("schools")
                .split(";"));
        Collections.sort(schools);
        if (!schools.get(0).equals("")) {
            for (String school : schools) {
                MenuItem changeSchoolItem = new MenuItem(school);
                changeSchoolItem.setOnAction((ActionEvent event) -> {
                    if (!school.equals(globalProperties
                            .getProperty("selectedSchool"))) {
                        globalProperties.setProperty("selectedSchool", school);
                        schoolPath = resourcePath + File.separator + school;
                        studentFilesPath = schoolPath + File.separator
                                + "Student Files";
                        storeGlobalProperties(globalProperties);
                        establishLocalProperties(localProperties);
                        activeSchoolTextProperty.setValue(globalProperties
                                .getProperty("selectedSchool"));
                        checkDatabase();
                        if (!localProperties.getProperty("listPath").isEmpty()) {
                            readStudentList(studentList, localProperties
                                    .getProperty("listPath"));
                            listStatusText.setTextFill(rgb(255, 255, 255));
                        } else {
                            studentList.clear();
                            listStatusText.setTextFill(rgb(255, 0, 0));
                        }
                        setListStatusTextProperty(
                                localProperties.getProperty("listPath"),
                                listStatusTextProperty);
                        setFilteredList(visibleStudentList,
                                observableStudentList, studentField);
                    } else {
                        errorAlert("In Use", "");
                    }
                });
                changeSchool.getItems().add(changeSchoolItem);
                MenuItem removeSchoolItem = new MenuItem(school);
                removeSchoolItem.setOnAction((ActionEvent event) -> {
                    if (!school.equals(globalProperties
                            .getProperty("selectedSchool"))) {
                        initRemoveSchoolStage(visibleStudentList,
                                observableStudentList, studentList, schools,
                                school, globalProperties, localProperties,
                                activeSchoolTextProperty,
                                listStatusTextProperty, primaryStage,
                                changeSchool, removeSchool, assignDatabase,
                                studentField, listStatusText);
                    } else {
                        errorAlert("In Use", "");
                    }
                });
                removeSchool.getItems().add(removeSchoolItem);
                MenuItem assignDatabaseItem = new MenuItem(school);
                assignDatabaseItem.setOnAction((ActionEvent event) -> {
                    if (!school.equals(globalProperties
                            .getProperty("selectedSchool"))) {
                        if (new File(resourcePath + File.separator + school)
                                .exists()) {
                            try {
                                confirmationAlert("File Exists");
                                try {
                                    File deleteSchoolPath
                                            = new File(schoolPath);
                                    String deleteSchool = deleteSchoolPath
                                            .getName();
                                    String[] splitSchoolList = globalProperties
                                            .getProperty("schools").split(";");
                                    List<String> schoolPropertyList = Arrays
                                            .asList(splitSchoolList);
                                    List<String> schoolList
                                            = new ArrayList<>(schoolPropertyList);
                                    schoolList.remove(deleteSchool);
                                    updateGlobalPropertiesFromList(
                                            globalProperties, schoolList);
                                    deleteDirectory(Paths.get(resourcePath
                                            + File.separator + school));
                                    Boolean check = new File(schoolPath)
                                            .renameTo(new File(resourcePath
                                                    + File.separator + school));
                                    schoolPath = resourcePath + File.separator
                                            + school;
                                    studentFilesPath = schoolPath
                                            + File.separator + "Student Files";
                                    File listFile = new File(localProperties
                                            .getProperty("listPath"));
                                    if (!listFile.getName().isEmpty()) {
                                        localProperties.setProperty("listPath",
                                                schoolPath + File.separator
                                                + listFile.getName());
                                    }
                                    storeLocalProperties(localProperties);
                                    globalProperties.setProperty(
                                            "selectedSchool", school);
                                    storeGlobalProperties(globalProperties);
                                    activeSchoolTextProperty
                                            .setValue(globalProperties
                                                    .getProperty(
                                                            "selectedSchool"));
                                    updateMenus(visibleStudentList,
                                            observableStudentList, studentList,
                                            activeSchoolTextProperty,
                                            listStatusTextProperty,
                                            globalProperties,
                                            localProperties, primaryStage,
                                            changeSchool, removeSchool,
                                            assignDatabase, listStatusText,
                                            studentField);
                                } catch (IOException io) {
                                    errorAlert("File Not Found", getTrace(io));
                                }
                            } catch (IllegalArgumentException ia) {
                            }
                        }
                    } else {
                        errorAlert("In Use", "");
                    }
                });
                assignDatabase.getItems().add(assignDatabaseItem);
            }
        }
    }

    private void showContextMenu(TextArea noteArea, Student student) {
        final ContextMenu contextMenu = new ContextMenu();
        final MenuItem print = new MenuItem("Print Notes");
        print.setOnAction((ActionEvent event) -> {
            List<String> notes = Arrays.asList(noteArea.getText().split("\n"));
            String fileDate = LocalDateTime.now().format(DateTimeFormatter
                    .ofPattern("yyyy-MM-dd"));
            String printDate = LocalDateTime.now().format(DateTimeFormatter
                    .ofPattern("MMM d, yyyy"));
            String printTime = LocalDateTime.now().format(DateTimeFormatter
                    .ofPattern("h:mm a"));
            String printFile = schoolPath + File.separator + "Student Files"
                    + File.separator + student.getFullName() + File.separator
                    + fileDate + " Notes.txt";
            String header = "Notes for  " + student.getFullName() + " on "
                    + printDate + " at " + printTime + ":";
            print(notes, printFile, header);
        });
        contextMenu.getItems().add(print);
        noteArea.setContextMenu(contextMenu);
    }

    private Label createStatusText(StringProperty inputTxt) {
        Label statusText = new Label();
        statusText.setTextOverrun(LEADING_ELLIPSIS);
        statusText.setId("statusText");
        statusText.textProperty().bind(inputTxt);
        return statusText;
    }

    private void initAddStudentStage(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, TextField studentField,
            StringProperty listStatusTextProperty,
            Properties localProperties) {
        Stage addStudentStage = new Stage();
        addStudentStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        addStudentStage.setTitle("Add Student");
        addStudentStage.initModality(Modality.APPLICATION_MODAL);
        Label addText = new Label("Type the name of the student you would like "
                + "to add and press \"OK\"");
        TextField addField = new TextField();
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            String fieldName = addField.getText();
            if (fieldName != null && !fieldName.equals("")) {
                String[] fileName = new File(
                        localProperties.getProperty("listPath")).getName()
                        .split(" ");
                if (!fileName[0].equals("Modified")) {
                    localProperties.setProperty("listPath", resourcePath
                            + File.separator + "Modified " + new File(
                                    localProperties.getProperty("listPath"))
                            .getName());
                    storeLocalProperties(localProperties);
                    setListStatusTextProperty(localProperties
                            .getProperty("listPath"),
                            listStatusTextProperty);
                }
                addStudentToList(studentList, fieldName,
                        localProperties.getProperty("listPath"));
                setFilteredList(visibleStudentList, observableStudentList,
                        studentField);
                addStudentStage.close();
            }
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            addStudentStage.close();
        });

        Scene addScene = new Scene(addStudentStageLayout(addText, addField,
                okButton, cancelButton));
        addScene
                .getStylesheets().add(StudentTracker.class
                        .getResource(
                                "Main.css").toExternalForm());
        addStudentStage.setScene(addScene);
        addStudentStage.show();
    }

    private void initAddSchoolStage(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty activeSchoolTextProperty,
            StringProperty listStatusTextProperty, Properties globalProperties,
            Properties localProperties, Stage primaryStage, Menu changeSchool,
            Menu removeSchool, Menu assignDatabase, Label listStatusText,
            TextField studentField) {
        Stage addSchoolStage = new Stage();
        addSchoolStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        addSchoolStage.setTitle("Add School");
        addSchoolStage.initModality(Modality.APPLICATION_MODAL);
        Label addText = new Label("Type the name of the school you would like "
                + "to add and press OK");
        TextField addField = new TextField();
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            String fieldName = addField.getText();
            if (fieldName != null && !fieldName.equals("")) {
                if (globalProperties.getProperty("schools").equals("")) {
                    globalProperties.setProperty("schools", fieldName);
                } else {
                    globalProperties.setProperty("schools",
                            globalProperties.getProperty("schools") + ";"
                            + fieldName);
                }
                storeGlobalProperties(globalProperties);
                schoolPath = resourcePath + File.separator
                        + fieldName;
                new File(schoolPath).mkdir();
                new File(schoolPath + File.separator + "students").mkdir();
                checkDatabase();
                Properties newLocalProperties = new Properties();
                establishLocalProperties(newLocalProperties);
                schoolPath = resourcePath + File.separator + globalProperties
                        .getProperty("selectedSchool");
                updateMenus(visibleStudentList, observableStudentList,
                        studentList, activeSchoolTextProperty,
                        listStatusTextProperty, globalProperties,
                        localProperties, primaryStage, changeSchool,
                        removeSchool, assignDatabase, listStatusText,
                        studentField);
                addSchoolStage.close();
            }
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            addSchoolStage.close();
        });

        Scene addScene = new Scene(addStudentStageLayout(addText, addField,
                okButton, cancelButton));
        addScene
                .getStylesheets().add(StudentTracker.class
                        .getResource(
                                "Main.css").toExternalForm());
        addSchoolStage.setScene(addScene);
        addSchoolStage.show();
    }

    private void initClientStage(List<String> clientList, String group) {
        final ListView<String> visibleGroupList = new ListView(FXCollections
                .observableList(clientList));
        visibleGroupList.setEditable(false);
        Button printButton = initPrintButton(clientList, group);
        Stage clientStage = new Stage();
        clientStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            clientStage.close();
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            clientStage.close();
        });
        clientStage.setTitle("Students in " + group + " Counseling");
        clientStage.initModality(Modality.APPLICATION_MODAL);
        HBox content = new HBox();
        content.getStyleClass().setAll("h-box");
        content.getChildren().addAll(visibleGroupList, printButton);
        HBox exitButtons = new HBox();
        exitButtons.getChildren().addAll(okButton, cancelButton);
        exitButtons.setAlignment(Pos.CENTER);
        exitButtons.getStyleClass().setAll("h-box");
        exitButtons.setId("exit-button");
        VBox individualStageLayout = new VBox();
        individualStageLayout.getStyleClass().setAll("layout-box");
        individualStageLayout.getChildren().addAll(content, exitButtons);
        Scene indScene = new Scene(individualStageLayout);
        indScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        clientStage.setScene(indScene);
        clientStage.show();
    }

    private void initArchiveStage(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty listStatusTextProperty,
            Properties localProperties, TextField studentField) {
        List<String> archivedStudents = collectArchivedStudents();
        ListView<String> archiveList = new ListView<>(FXCollections
                .observableList(archivedStudents));
        archiveList.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2 && !event.isConsumed()) {
                String selectedStudent = archiveList.getSelectionModel()
                        .getSelectedItem().trim();
                if (selectedStudent != null) {
                    Student student = new Student(selectedStudent);
                    File target = new File(studentFilesPath + File.separator
                            + student.getFullName());
                    target.mkdir();
                    accessArchive(student);
                    initArchivedStudentStage(student);
                }
            }
        });
        Button returnStudentButton = initButton("ReturnStudentButton.png", 110,
                66);
        returnStudentButton.setOnAction((ActionEvent event) -> {
            String selectedStudent = archiveList.getSelectionModel()
                    .getSelectedItem().trim();
            if (selectedStudent != null) {
                try {
                    confirmationAlert("Return Student");
                    String[] fileName = new File(
                            localProperties.getProperty("listPath")).getName()
                            .split(" ");
                    if (!fileName[0].equals("Modified")) {
                        localProperties.setProperty("listPath", resourcePath
                                + File.separator
                                + "Modified " + new File(
                                        localProperties.getProperty("listPath"))
                                .getName());
                        storeLocalProperties(localProperties);
                        setListStatusTextProperty(localProperties
                                .getProperty("listPath"),
                                listStatusTextProperty);
                    }
                    transferTableEntry(selectedStudent, "clients", "archive");
                    addStudentToList(studentList, selectedStudent,
                            localProperties.getProperty("listPath"));
                    setFilteredList(visibleStudentList, observableStudentList,
                            studentField);
                    archivedStudents.remove(selectedStudent);
                    archiveList.refresh();
                } catch (IllegalArgumentException ia) {
                }
            }
        });
        Button printButton = initPrintButton(archivedStudents, "Archive");
        Stage archiveStage = new Stage();
        archiveStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            archiveStage.close();
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            archiveStage.close();
        });
        HBox exitButtons = new HBox();
        exitButtons.setId("exit-button");
        exitButtons.getChildren().addAll(okButton, cancelButton);
        VBox buttonBox = new VBox();
        buttonBox.getChildren().addAll(returnStudentButton, printButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getStyleClass().setAll("v-box");
        HBox content = new HBox();
        content.getStyleClass().setAll("h-box");
        content.getChildren().addAll(archiveList, buttonBox);
        VBox archiveStageLayout = new VBox();
        archiveStageLayout.getChildren().addAll(content, exitButtons);
        archiveStageLayout.getStyleClass().setAll("layout-box");
        archiveStage.setTitle("Archived Students");
        archiveStage.initModality(Modality.APPLICATION_MODAL);
        Scene indScene = new Scene(archiveStageLayout);
        indScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        archiveStage.setScene(indScene);
        archiveStage.show();
    }

    private void initRemoveSchoolStage(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, List<String> schools, String school,
            Properties globalProperties, Properties localProperties,
            StringProperty activeSchoolTextProperty,
            StringProperty listStatusTextProperty, Stage primaryStage,
            Menu changeSchool, Menu removeSchool, Menu assignDatabase,
            TextField studentField, Label listStatusText) {
        Stage removeSchoolStage = new Stage();
        removeSchoolStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        removeSchoolStage.setTitle("Remove School");
        removeSchoolStage.initModality(Modality.APPLICATION_MODAL);
        Label removeText = new Label("Removing " + school + " will delete its "
                + "database and all related student data. Continue?");
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            try {
                deleteDirectory(Paths.get(resourcePath + File.separator
                        + school));
                List<String> removeList = new ArrayList<>(schools);
                Collections.sort(removeList);
                Boolean check = removeList.remove(school);
                updateGlobalPropertiesFromList(globalProperties, removeList);
                updateMenus(visibleStudentList,
                        observableStudentList, studentList,
                        activeSchoolTextProperty, listStatusTextProperty,
                        globalProperties, localProperties, primaryStage,
                        changeSchool, removeSchool, assignDatabase,
                        listStatusText, studentField);
                storeGlobalProperties(globalProperties);

            } catch (IOException io) {
                errorAlert("File Not Found", getTrace(io));
            }
            removeSchoolStage.close();
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            removeSchoolStage.close();
        });

        Scene removeScene = new Scene(removeSchoolStageLayout(removeText,
                okButton, cancelButton));
        removeScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        removeSchoolStage.setScene(removeScene);
        removeSchoolStage.show();
    }

    private void initArchivedStudentStage(Student student) {
        String studentName = student.getFullName();
        Stage studentStage = new Stage();
        studentStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        studentStage.initModality(Modality.APPLICATION_MODAL);
        studentStage.setTitle("Counseling for " + studentName);
        CheckBox indBox = new CheckBox("Individual") {
            @Override
            public void arm() {
            }
        };
        indBox.setId("indB");
        setCheck(indBox, student);
        CheckBox groupBox = new CheckBox("Group") {
            @Override
            public void arm() {
            }
        };
        groupBox.setId("groupB");
        setCheck(groupBox, student);
        groupBox.isMouseTransparent();
        CheckBox checkInBox = new CheckBox("Check-in") {
            @Override
            public void arm() {
            }
        };
        checkInBox.setId("checkInB");
        setCheck(checkInBox, student);
        checkInBox.isMouseTransparent();
        Label viewDate = new Label("View dates");
        viewDate.setId("mod-date");
        viewDate.setOnMouseClicked((MouseEvent event) -> {
            viewStudentDialog(student);
        });
        Button viewWalkInButton = initButton("ViewWalkInButton.png", 110, 66);
        viewWalkInButton.setOnAction((ActionEvent event) -> {
            initViewWalkInStage(student);
        });
        Button detailsButton = initButton("DetailsButton.png", 110, 66);
        detailsButton.setOnAction((ActionEvent event) -> {
            initArchivedDetailsStage(student);
        });
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            studentStage.close();
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            studentStage.close();
        });
        Label title = new Label("Counseling enrollment for " + studentName);
        Scene studentScene = new Scene(archivedStudentStageLayout(indBox, groupBox,
                checkInBox, viewDate, viewWalkInButton, detailsButton, okButton,
                cancelButton, title));
        studentScene
                .getStylesheets().add(StudentTracker.class
                        .getResource(
                                "Main.css").toExternalForm());
        studentStage.setScene(studentScene);
        studentStage.show();
    }

    private void initStudentStage(Student student) {
        String studentName = student.getFullName();
        Stage studentStage = new Stage();
        studentStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        studentStage.initModality(Modality.APPLICATION_MODAL);
        studentStage.setTitle("Counseling for " + studentName);
        CheckBox indBox = new CheckBox("Individual");
        indBox.setId("indB");
        setCheck(indBox, student);

        indBox.setOnMouseClicked((MouseEvent a) -> {
            if (student.getStartInd() == null && student.getEndInd() == null) {
                addBoxFunction(indBox, student, Category.indStart);
            } else if (student.getEndInd() == null) {
                addBoxFunction(indBox, student, Category.indEnd);
            } else {
                indBox.setIndeterminate(true);
            }
        });

        CheckBox groupBox = new CheckBox("Group");
        groupBox.setId("groupB");
        setCheck(groupBox, student);
        groupBox.setOnMouseClicked((MouseEvent a) -> {
            if (student.getStartGroup() == null
                    && student.getEndGroup() == null) {
                addBoxFunction(groupBox, student, Category.groupStart);
            } else if (student.getEndGroup() == null) {
                addBoxFunction(groupBox, student, Category.groupEnd);
            } else {
                groupBox.setIndeterminate(true);
            }
        });
        CheckBox checkInBox = new CheckBox("Check-in");
        checkInBox.setId("checkInB");
        setCheck(checkInBox, student);
        checkInBox.setOnMouseClicked((MouseEvent a) -> {
            if (student.getStartCheckIn() == null
                    && student.getEndCheckIn() == null) {
                addBoxFunction(checkInBox, student, Category.checkInStart);
            } else if (student.getEndCheckIn() == null) {
                addBoxFunction(checkInBox, student, Category.checkInEnd);
            } else {
                checkInBox.setIndeterminate(true);
            }
        });

        Label modDate = new Label("Modify dates");
        modDate.setId("mod-date");
        modDate.setOnMouseClicked((MouseEvent event) -> {
            modifyStudentDialog(student, indBox, groupBox, checkInBox);
        });
        Button addWalkInButton = createAddWalkInButton(student);
        Button viewWalkInButton = initButton("ViewWalkInButton.png", 110, 66);
        viewWalkInButton.setOnAction((ActionEvent event) -> {
            initViewWalkInStage(student);
        });
        Button detailsButton = createDetailsButton(student);
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            studentStage.close();
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            studentStage.close();
        });
        Label title = new Label("Counseling enrollment for " + studentName);
        Scene studentScene = new Scene(studentStageLayout(indBox, groupBox,
                checkInBox, modDate, addWalkInButton, viewWalkInButton,
                detailsButton, okButton, cancelButton, title));
        studentScene.getStylesheets().add(StudentTracker.class
                .getResource("Main.css").toExternalForm());
        studentStage.setScene(studentScene);
        studentStage.show();
    }

    private void initViewWalkInStage(Student student) {
        Stage viewWalkInStage = new Stage();
        viewWalkInStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        viewWalkInStage.initModality(Modality.APPLICATION_MODAL);
        viewWalkInStage.setTitle(student.getFullName() + "'s Walk-ins");
        List<String> walkInList = Arrays.asList(student.getWalkIns().split(";"));
        ListView<String> visibleWalkInList = new ListView<>();
        visibleWalkInList.setItems(FXCollections.observableList(walkInList));
        visibleWalkInList.isMouseTransparent();
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            viewWalkInStage.close();
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            viewWalkInStage.close();
        });
        HBox exitButtons = new HBox();
        exitButtons.getChildren().addAll(okButton, cancelButton);
        exitButtons.setId("exit-button");
        VBox layout = new VBox();
        layout.getChildren().addAll(visibleWalkInList, exitButtons);
        Scene scene = new Scene(layout);
        scene.getStylesheets().add(StudentTracker.class
                .getResource("Main.css").toExternalForm());
        viewWalkInStage.setScene(scene);
        viewWalkInStage.show();
    }

    private void initArchivedDetailsStage(Student student) {
        Stage detailsStage = new Stage();
        detailsStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        detailsStage.setTitle(student.getFullName() + "'s Details");
        detailsStage.initModality(Modality.APPLICATION_MODAL);
        CheckBox iepBox = new CheckBox("IEP") {
            @Override
            public void arm() {
            }
        };
        if (student.getIEP()) {
            iepBox.setSelected(true);
        }
        iepBox.isDisabled();
        CheckBox ffBox = new CheckBox("504") {
            @Override
            public void arm() {
            }
        };
        if (student.get504()) {
            ffBox.setSelected(true);
        }
        ffBox.isDisabled();
        CheckBox evalBox = new CheckBox("Eval in process") {
            @Override
            public void arm() {
            }
        };
        if (student.getEval()) {
            evalBox.setSelected(true);
        }
        evalBox.isDisabled();
        Label formText = new Label("Forms:");
        ListView<String> visibleFormList = new ListView<>();
        visibleFormList.setEditable(false);
        visibleFormList.setMaxSize(280, 120);
        List forms = new ArrayList<>();
        if (!student.getForms().isEmpty()) {
            forms.add(student.getForms().split(";"));
        }

        visibleFormList.setItems(FXCollections.observableList(forms));
        visibleFormList.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2 && !event.isConsumed()) {

                try {
                    if (Desktop.isDesktopSupported()) {
                        String fileName = visibleFormList.getSelectionModel()
                                .getSelectedItem();
                        if (fileName != null) {
                            Desktop.getDesktop().open(new File(studentFilesPath
                                    + File.separator + student.getFullName()
                                    + File.separator + fileName));
                        }
                    }
                } catch (IOException io) {
                }
            }
        });

        Label noteText = new Label("Notes:");
        TextArea noteArea = new TextArea();
        noteArea.setText(student.getNotes());
        noteArea.setMaxSize(280, 500);
        noteArea.setWrapText(true);
        noteArea.setEditable(false);
        noteArea.setOnMouseClicked((MouseEvent event) -> {
            if (event.getButton().equals(MouseButton.SECONDARY)) {
                showContextMenu(noteArea, student);
            }
        });
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            detailsStage.close();
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            detailsStage.close();
        });

        Scene detailsScene = new Scene(archivedDetailsStageLayout(iepBox, ffBox,
                evalBox, formText, visibleFormList, noteText,
                noteArea, okButton, cancelButton), 300, 500);
        detailsScene
                .getStylesheets().add(StudentTracker.class
                        .getResource(
                                "Main.css").toExternalForm());
        detailsStage.setScene(detailsScene);
        detailsStage.show();
    }

    private void initDetailsStage(Student student) {
        Stage detailsStage = new Stage();
        detailsStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        detailsStage.setTitle(student.getFullName() + "'s Details");
        detailsStage.initModality(Modality.APPLICATION_MODAL);
        CheckBox iepBox = new CheckBox("IEP");
        if (student.getIEP()) {
            iepBox.setSelected(true);
        }
        iepBox.setOnAction((ActionEvent event) -> {
            student.toggleIEP();
            modifyDatabase(Category.hasIEP, student);
            iepBox.setSelected(student.getIEP());
        });
        CheckBox ffBox = new CheckBox("504");
        if (student.get504()) {
            ffBox.setSelected(true);
        }
        ffBox.setOnAction((ActionEvent event) -> {
            student.toggle504();
            modifyDatabase(Category.has504, student);
            ffBox.setSelected(student.get504());
        });
        CheckBox evalBox = new CheckBox("Eval in process");
        if (student.getEval()) {
            evalBox.setSelected(true);
        }
        evalBox.setOnAction((ActionEvent event) -> {
            student.toggleEval();
            modifyDatabase(Category.hasEval, student);
            evalBox.setSelected(student.getEval());
        });
        Label formText = new Label("Forms:");
        ListView<String> visibleFormList = new ListView<>();
        visibleFormList.setEditable(false);
        visibleFormList.setMaxSize(280, 120);
        List forms = new ArrayList<>();
        if (!student.getForms().isEmpty()) {
            forms.add(student.getForms().split(";"));
        }

        visibleFormList.setItems(FXCollections.observableList(forms));
        visibleFormList.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2 && !event.isConsumed()) {

                try {
                    if (Desktop.isDesktopSupported()) {
                        String fileName = visibleFormList.getSelectionModel()
                                .getSelectedItem();
                        if (fileName != null) {
                            Desktop.getDesktop().open(new File(studentFilesPath
                                    + File.separator + student.getFullName()
                                    + File.separator + fileName));
                        }
                    }
                } catch (IOException io) {
                }
            }
        });

        Button addFormButton = initAddFormButton(student, detailsStage, forms,
                visibleFormList);

        Button removeFormButton = initRemoveFormButton(student, detailsStage,
                forms, visibleFormList);

        Label noteText = new Label("Notes:");
        TextArea noteArea = new TextArea();
        noteArea.setText(student.getNotes());
        noteArea.setMaxSize(280, 500);
        noteArea.setWrapText(true);
        noteArea.setOnMouseClicked((MouseEvent event) -> {
            if (event.getButton().equals(MouseButton.SECONDARY)) {
                showContextMenu(noteArea, student);
            }
        });
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            student.setNotes(noteArea.getText());
            modifyDatabase(Category.notes, student);
            detailsStage.close();
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            detailsStage.close();
        });

        Scene detailsScene = new Scene(detailsStageLayout(iepBox, ffBox,
                evalBox, formText, visibleFormList,
                addFormButton, removeFormButton, noteText,
                noteArea, okButton, cancelButton), 300, 500);
        detailsScene
                .getStylesheets().add(StudentTracker.class
                        .getResource(
                                "Main.css").toExternalForm());
        detailsStage.setScene(detailsScene);
        detailsStage.show();
    }

    private void viewStudentDialog(Student student) {
        Label modifyPrompt = new Label("The following are "
                + student.getFullName() + "'s counseling start and end dates.");
        Label startIndText = new Label("Independent counseling start date:");
        Label endIndText = new Label("Independent counseling end date:");
        Label startGroupText = new Label("Group counseling start date:");
        Label endGroupText = new Label("Group counseling end date:");
        Label startCheckInText = new Label("Check-in counseling start date:");
        Label endCheckInText = new Label("Check-in counseling end date:");

        TextField startIndField = new TextField();
        TextField endIndField = new TextField();
        TextField startGroupField = new TextField();
        TextField endGroupField = new TextField();
        TextField startCheckInField = new TextField();
        TextField endCheckInField = new TextField();
        startIndField.setText(student.getStartInd());
        startIndField.setEditable(false);
        endIndField.setText(student.getEndInd());
        endIndField.setEditable(false);
        startGroupField.setText(student.getStartGroup());
        startGroupField.setEditable(false);
        endGroupField.setText(student.getEndGroup());
        endGroupField.setEditable(false);
        startCheckInField.setText(student.getStartCheckIn());
        startCheckInField.setEditable(false);
        endCheckInField.setText(student.getEndCheckIn());
        endCheckInField.setEditable(false);
        Stage modStudentStage = new Stage();
        modStudentStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        modStudentStage.setTitle(student.getFullName() + "'s"
                + " Enrollment Dates");
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            modStudentStage.close();
        });

        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            modStudentStage.close();
        });
        HBox buttonBox = new HBox();
        buttonBox.getChildren().addAll(okButton, cancelButton);
        buttonBox.getStyleClass().setAll("box");
        buttonBox.setId("exit-button");
        modStudentStage.initModality(Modality.APPLICATION_MODAL);
        GridPane datePane = new GridPane();
        datePane.setId("date-pane");
        datePane.add(startIndText, 1, 1);
        datePane.add(endIndText, 1, 2);
        datePane.add(startGroupText, 1, 3);
        datePane.add(endGroupText, 1, 4);
        datePane.add(startCheckInText, 1, 5);
        datePane.add(endCheckInText, 1, 6);
        datePane.add(startIndField, 2, 1);
        datePane.add(endIndField, 2, 2);
        datePane.add(startGroupField, 2, 3);
        datePane.add(endGroupField, 2, 4);
        datePane.add(startCheckInField, 2, 5);
        datePane.add(endCheckInField, 2, 6);
        VBox layout = new VBox();
        layout.getChildren().addAll(modifyPrompt, datePane, buttonBox);
        layout.getStyleClass().setAll("layout-box");
        Scene scene = new Scene(layout);
        scene.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
        modStudentStage.setScene(scene);
        modStudentStage.show();
    }

    private void modifyStudentDialog(Student student, CheckBox indBox,
            CheckBox groupBox, CheckBox checkInBox) {

        Label modifyPrompt = new Label("Replace any incorrect dates and press"
                + " \"OK\".");
        Label startIndText = new Label("Independent counseling start date:");
        Label endIndText = new Label("Independent counseling end date:");
        Label startGroupText = new Label("Group counseling start date:");
        Label endGroupText = new Label("Group counseling end date:");
        Label startCheckInText = new Label("Check-in counseling start date:");
        Label endCheckInText = new Label("Check-in counseling end date:");

        TextField startIndField = new TextField();
        TextField endIndField = new TextField();
        TextField startGroupField = new TextField();
        TextField endGroupField = new TextField();
        TextField startCheckInField = new TextField();
        TextField endCheckInField = new TextField();
        startIndField.setText(student.getStartInd());
        endIndField.setText(student.getEndInd());
        startGroupField.setText(student.getStartGroup());
        endGroupField.setText(student.getEndGroup());
        startCheckInField.setText(student.getStartCheckIn());
        endCheckInField.setText(student.getEndCheckIn());
        Stage modStudentStage = new Stage();
        modStudentStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        modStudentStage.setTitle("Modify " + student.getFullName() + "'s"
                + " Enrollment Dates");
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            try {
                checkFieldText(startIndField, student);
                checkFieldText(endIndField, student);
                checkFieldText(startGroupField, student);
                checkFieldText(endGroupField, student);
                checkFieldText(startCheckInField, student);
                checkFieldText(endCheckInField, student);

                if (startIndField.getText() == null || startIndField.getText()
                        .equals("")) {
                    student.setStartInd(null);
                } else {
                    student.setStartInd(startIndField.getText());
                }
                if (endIndField.getText() == null || endIndField.getText()
                        .equals("")) {
                    student.setEndInd(null);
                } else {
                    student.setEndInd(endIndField.getText());
                }

                if (startGroupField.getText() == null || startGroupField
                        .getText().equals("")) {
                    student.setStartGroup(null);
                } else {
                    student.setStartGroup(startGroupField.getText());
                }

                if (endGroupField.getText() == null || endGroupField.getText()
                        .equals("")) {
                    student.setEndGroup(null);
                } else {
                    student.setEndGroup(endGroupField.getText());
                }

                if (startCheckInField.getText() == null || startCheckInField
                        .getText().equals("")) {
                    student.setStartCheckIn(null);
                } else {
                    student.setStartCheckIn(startCheckInField.getText());
                }

                if (endCheckInField.getText() == null || endCheckInField
                        .getText().equals("")) {
                    student.setEndCheckIn(null);
                } else {
                    student.setEndCheckIn(endCheckInField.getText());
                }
                refreshDatabase(student);
                modStudentStage.close();
            } catch (DataFormatException df) {
                errorAlert("Improper Format", getTrace(df));
            }

            setCheck(indBox, student);
            setCheck(groupBox, student);
            setCheck(checkInBox, student);
        });

        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            modStudentStage.close();
        });
        HBox buttonBox = new HBox();
        buttonBox.getChildren().addAll(okButton, cancelButton);
        buttonBox.getStyleClass().setAll("box");
        buttonBox.setId("exit-button");
        modStudentStage.initModality(Modality.APPLICATION_MODAL);
        GridPane datePane = new GridPane();
        datePane.setId("date-pane");
        datePane.add(startIndText, 1, 1);
        datePane.add(endIndText, 1, 2);
        datePane.add(startGroupText, 1, 3);
        datePane.add(endGroupText, 1, 4);
        datePane.add(startCheckInText, 1, 5);
        datePane.add(endCheckInText, 1, 6);
        datePane.add(startIndField, 2, 1);
        datePane.add(endIndField, 2, 2);
        datePane.add(startGroupField, 2, 3);
        datePane.add(endGroupField, 2, 4);
        datePane.add(startCheckInField, 2, 5);
        datePane.add(endCheckInField, 2, 6);
        VBox layout = new VBox();
        layout.getChildren().addAll(modifyPrompt, datePane, buttonBox);
        layout.getStyleClass().setAll("layout-box");
        Scene scene = new Scene(layout);
        scene.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
        modStudentStage.setScene(scene);
        modStudentStage.show();

    }

    private void errorAlert(String error, String trace) {
        String alertText = null;
        switch (error) {
            case "Improper Format":
                alertText = "Specified date was not formatted properly!"
                        + " Please use \"mm-dd-yyyy\" format.";
                break;
            case "List Not Found":
                alertText = "Specified student list was not found!"
                        + "Please choose a different list.";
                break;
            case "Read Local Properties":
                alertText = "Unable to load local properties file!";
                break;
            case "Read Global Properties":
                alertText = "Unable to load global properties file!";
                break;
            case "Write Local Properties":
                alertText = "Unable to modify local properties file!";
                break;
            case "Write Global Properties":
                alertText = "Unable to modify global properties file!";
                break;
            case "File Not Found":
                alertText = "File was not found!";
                break;
            case "In Use":
                alertText = "Already in use!";
                break;
            case "No Student":
                alertText = "No data for selected student, cannot archive.";
                break;
            default:
                alertText = "";
        }
        Alert errAlert = new Alert(Alert.AlertType.ERROR, alertText + "\n"
                + trace);
        Stage dialogStage = (Stage) errAlert.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        DialogPane dialogPane = errAlert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
        dialogPane.getStyleClass().add("myDialog");
        Button okButton = (Button) errAlert.getDialogPane()
                .lookupButton(ButtonType.OK);
        okButton.setText(null);
        Image okImage = new Image("/studenttracker/Icons/OKButton.png", 55, 33,
                true, true);
        okButton.setGraphic(new ImageView(okImage));
        errAlert.showAndWait();
    }

    private void confirmationAlert(String type) {
        String alertText = null;
        switch (type) {
            case "Archive":
                alertText = "Archiving a student will remove it from the"
                        + " current list and add it to the archive. Continue?";
                break;
            case "File Exists":
                alertText = "File already exists. Overwrite?";
                break;
            case "Return Student":
                alertText = "Returning a student to the active student list"
                        + " removes them from the archive. Continue?";
                break;
            case "School Overwrite":
                alertText = "Proceeding will delete all data from the school"
                        + " you are assigning to. Continue?";
            default:
                break;
        }
        Alert confAlert = new Alert(Alert.AlertType.CONFIRMATION, alertText);
        Stage dialogStage = (Stage) confAlert.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        DialogPane dialogPane = confAlert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
        dialogPane.getStyleClass().add("dialog");
        Button okButton = (Button) confAlert.getDialogPane()
                .lookupButton(ButtonType.OK);
        Image okImage = new Image(
                "/studenttracker/Icons/OKButton.png", 55, 33, true,
                true);
        okButton.setGraphic(new ImageView(okImage));
        okButton.setText(null);
        Button cancelButton = (Button) confAlert
                .getDialogPane().lookupButton(ButtonType.CANCEL);
        Image cancelImage = new Image(
                "/studenttracker/Icons/CancelButton.png", 55, 33,
                true, true);
        cancelButton.setGraphic(new ImageView(cancelImage));
        cancelButton.setText(null);
        confAlert.showAndWait().ifPresent(response -> {

            if (response != ButtonType.OK) {
                throw new IllegalArgumentException();
            }
        });
    }

    private void sqlErrorAlert(String trace) {
        Alert sqlErrorAlert = new Alert(Alert.AlertType.ERROR,
                "Error interfacing with database:\n" + trace);
        DialogPane dialogPane = sqlErrorAlert.getDialogPane();
        Stage dialogStage = (Stage) sqlErrorAlert.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(new Image("/studenttracker/Icons/StudentTrackerIcon.png"));
        dialogPane.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
        Button okButton = (Button) sqlErrorAlert.getDialogPane()
                .lookupButton(ButtonType.OK);
        Image okImage = new Image("/studenttracker/Icons/OKButton.png", 55, 33,
                true, true);
        okButton.setGraphic(new ImageView(okImage));
        okButton.setText(null);
        sqlErrorAlert.showAndWait();
    }

    private VBox addStudentStageLayout(Label addText,
            TextField addField, Button okButton, Button cancelButton) {
        HBox buttons = new HBox();
        buttons.getChildren().addAll(okButton, cancelButton);
        buttons.getStyleClass().setAll("h-box");
        buttons.setId("exit-button");
        VBox addBox = new VBox();
        addBox.getChildren().addAll(addText, addField);
        VBox stageLayout = new VBox();
        stageLayout.setId("layout");
        stageLayout.getChildren().addAll(addBox, buttons);
        return stageLayout;
    }

    private VBox removeSchoolStageLayout(Label removeText, Button okButton,
            Button cancelButton) {
        HBox buttons = new HBox();
        buttons.setId("exit-button");
        buttons.getChildren().addAll(okButton, cancelButton);
        VBox layout = new VBox();

        layout.setId("layout");
        layout.getChildren().addAll(removeText, buttons);
        return layout;
    }

    private VBox archivedStudentStageLayout(CheckBox indBox, CheckBox groupBox,
            CheckBox checkInBox, Label viewDate,
            Button viewWalkInButton, Button detailsButton, Button okButton, Button cancelButton,
            Label title) {
        HBox buttons = new HBox();
        buttons.getStyleClass().setAll("h-box");
        buttons.getChildren().addAll(viewWalkInButton, detailsButton);
        buttons.setAlignment(Pos.CENTER);
        VBox checkBoxBox = new VBox();
        checkBoxBox.getChildren().addAll(indBox, groupBox, checkInBox, viewDate);
        HBox exitButtons = new HBox();
        exitButtons.getStyleClass().setAll("h-box");
        exitButtons.setId("exit-button");
        exitButtons.getChildren().addAll(okButton, cancelButton);
        VBox contentBox = new VBox();
        contentBox.setId("layout");
        contentBox.getChildren().addAll(title, checkBoxBox, buttons, exitButtons);
        return contentBox;
    }

    private VBox studentStageLayout(CheckBox indBox, CheckBox groupBox,
            CheckBox checkInBox, Label modDate, Button addWalkInButton,
            Button viewWalkInButton, Button detailsButton, Button okButton,
            Button cancelButton, Label title) {
        HBox buttons = new HBox();
        buttons.getStyleClass().setAll("h-box");
        buttons.getChildren().addAll(addWalkInButton, viewWalkInButton, detailsButton);
        buttons.setAlignment(Pos.CENTER);
        VBox checkBoxBox = new VBox();
        checkBoxBox.getChildren().addAll(indBox, groupBox, checkInBox, modDate);
        HBox exitButtons = new HBox();
        exitButtons.getStyleClass().setAll("h-box");
        exitButtons.setId("exit-button");
        exitButtons.getChildren().addAll(okButton, cancelButton);
        VBox contentBox = new VBox();
        contentBox.setId("layout");
        contentBox.getChildren().addAll(title, checkBoxBox, buttons, exitButtons);
        return contentBox;
    }

    private VBox archivedDetailsStageLayout(CheckBox iepBox, CheckBox ffBox,
            CheckBox evalBox, Label formText,
            ListView<String> visibleFormList, Label noteText,
            TextArea noteArea, Button okButton, Button cancelButton) {
        VBox noteLayout = new VBox();
        noteLayout.getChildren().addAll(noteText, noteArea);
        VBox formLayout = new VBox();
        formLayout.getChildren().addAll(formText, visibleFormList);
        VBox checkBoxLayout = new VBox();
        checkBoxLayout.getChildren().addAll(iepBox, ffBox, evalBox);
        HBox exitButtons = new HBox();
        exitButtons.getChildren().addAll(okButton, cancelButton);
        exitButtons.getStyleClass().setAll("h-box");
        exitButtons.setId("exit-button");
        VBox stageLayout = new VBox();
        stageLayout.setId("layout");
        stageLayout.getChildren().addAll(checkBoxLayout, formLayout, noteLayout,
                exitButtons);
        return stageLayout;
    }

    private VBox detailsStageLayout(CheckBox iepBox, CheckBox ffBox,
            CheckBox evalBox, Label formText, ListView<String> visibleFormList,
            Button addFormButton, Button removeFormButton, Label noteText,
            TextArea noteArea, Button okButton, Button cancelButton) {
        HBox formButtons = new HBox();
        formButtons.setAlignment(Pos.CENTER);
        formButtons.getStyleClass().setAll("h-box");
        formButtons.getChildren().addAll(addFormButton, removeFormButton);
        VBox noteLayout = new VBox();
        noteLayout.getChildren().addAll(noteText, noteArea);
        VBox formLayout = new VBox();
        formLayout.getChildren().addAll(formText, visibleFormList);
        VBox checkBoxLayout = new VBox();
        checkBoxLayout.getChildren().addAll(iepBox, ffBox, evalBox);
        HBox exitButtons = new HBox();
        exitButtons.getChildren().addAll(okButton, cancelButton);
        exitButtons.getStyleClass().setAll("h-box");
        exitButtons.setId("exit-button");
        VBox stageLayout = new VBox();
        stageLayout.setId("layout");
        stageLayout.getChildren().addAll(checkBoxLayout, formLayout,
                formButtons, noteLayout, exitButtons);
        return stageLayout;
    }

    private VBox primaryStageLayout(Label schoolLabel, Label activeSchool,
            Label listStatusText, Label fieldLabel, TextField studentField,
            ListView<String> visibleStudentList, Button setDirBtn,
            Label listLabel, Button viewIndButton, Button viewGroupButton,
            Button viewArchiveButton, Button addStudentButton,
            Button removeStudentButton, Button archiveStudentButton,
            MenuBar menuBar) {
        //Creates GUI elements and layout.

        VBox studentName = new VBox();
        studentName.getStyleClass().setAll("header-box");
        studentName.getChildren().addAll(fieldLabel, studentField);
        studentName.setAlignment(Pos.CENTER_LEFT);
        VBox school = new VBox();
        activeSchool.setId("activeSchool-label");
        school.getChildren().addAll(schoolLabel, activeSchool);
        VBox schoolAndStudent = new VBox();
        schoolAndStudent.getChildren().addAll(school, studentName);
        schoolAndStudent.getStyleClass().setAll("v-box");
        schoolAndStudent.setAlignment(Pos.CENTER);
        VBox viewButtonBox = new VBox();
        viewButtonBox.getChildren().addAll(viewIndButton, viewGroupButton,
                viewArchiveButton);
        viewButtonBox.getStyleClass().setAll("h-box");
        HBox listLayoutHBox = new HBox();
        listLayoutHBox.getChildren().addAll(visibleStudentList, viewButtonBox);
        listLayoutHBox.getStyleClass().setAll("h-box");
        VBox listLayoutVBox = new VBox();
        listLayoutVBox.getStyleClass().setAll("header-box");
        listLayoutVBox.getChildren().addAll(listLabel, listLayoutHBox);
        HBox modifyListButtonBox = new HBox();
        modifyListButtonBox.getChildren().addAll(addStudentButton,
                removeStudentButton, archiveStudentButton);
        modifyListButtonBox.getStyleClass().setAll("h-box");
        HBox dirBtnBox = new HBox();
        dirBtnBox.setAlignment(Pos.CENTER_RIGHT);
        dirBtnBox.getChildren().add(setDirBtn);
        GridPane layoutPane = new GridPane();
        layoutPane.getStyleClass().setAll("grid-pane");
        layoutPane.add(schoolAndStudent, 0, 0);
        layoutPane.add(listLayoutVBox, 1, 0);
        layoutPane.add(dirBtnBox, 0, 1);
        setDirBtn.setAlignment(Pos.CENTER_RIGHT);
        layoutPane.add(modifyListButtonBox, 1, 1);
        VBox layout = new VBox();
        layout.getChildren().addAll(menuBar, layoutPane, listStatusText);
        layout.getStyleClass().setAll("layout-box");
        VBox windowContent = new VBox();
        windowContent.getChildren().addAll(menuBar, layout);
        return windowContent;
    }

    private void setListStatusTextProperty(String listPath,
            StringProperty listStatusTextProperty) {
        if (listPath.isEmpty()) {
            listStatusTextProperty.set("Selected Student List:\nNONE");
        } else {
            listStatusTextProperty.set("Selected Student List:\n"
                    + listPath);
        }
    }

    private void updateGlobalPropertiesFromList(Properties globalProperties,
            List<String> list) {
        if (!list.isEmpty()) {
            StringBuilder schoolSB = new StringBuilder(list
                    .get(0));
            for (int i = 1; i < list.size(); i++) {
                schoolSB.append(";");
                schoolSB.append(list.get(i));
            }
            globalProperties.setProperty("schools",
                    schoolSB.toString());
        } else {
            globalProperties.setProperty("schools", "");
        }
    }

    private void checkDatabase() {
        File file = new File(schoolPath + File.separator + File.separator
                + dbName + ".db");
        if (!file.exists()) {
            Connection conn = null;
            Statement stat = null;
            try {
                Class.forName("org.sqlite.JDBC");
                conn = DriverManager.getConnection("jdbc:sqlite:"
                        + schoolPath + File.separator + dbName + ".db");
                stat = conn.createStatement();

                stat.executeUpdate("create table clients (fullName, firstName, "
                        + "lastName, indStart, indEnd, groupStart, groupEnd, "
                        + "checkInStart, checkInEnd, walkIns, forms, notes, "
                        + "hasIEP, has504, hasEval);");
                stat.executeUpdate("create table archive (fullName, firstName, "
                        + "lastName, indStart, indEnd, groupStart, groupEnd, "
                        + "checkInStart, checkInEnd, walkIns, forms, notes, "
                        + "hasIEP, has504, hasEval);");

            } catch (ClassNotFoundException | SQLException sql) {
                sqlErrorAlert(getTrace(sql));
            } finally {
                try {
                    if (stat != null) {
                        stat.close();
                    }
                } catch (Exception e) {
                }
                try {
                    if (conn != null) {
                        conn.close();

                    }
                } catch (Exception e) {
                }
            }
        }
    }

    private String getTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private void removeFromStudentTable(String name) {
        Connection conn = null;
        Statement stat = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + schoolPath
                    + File.separator + dbName + ".db");
            stat = conn.createStatement();
            stat.executeUpdate("delete from clients where "
                    + Category.fullName.toString() + " = '"
                    + name + "';");
        } catch (ClassNotFoundException | SQLException sql) {
            sqlErrorAlert(getTrace(sql));
        } finally {
            try {
                if (stat != null) {
                    stat.close();
                }
            } catch (Exception e) {
            }
            try {
                if (conn != null) {
                    conn.close();

                }
            } catch (Exception e) {
            }
        }
    }

    private void modifyDatabase(Category cat, Student student) {
        Connection conn = null;
        PreparedStatement update = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:"
                    + schoolPath + File.separator + dbName + ".db");
            String val = null;
            switch (cat.toString()) {
                case "indStart":
                    val = student.getStartInd();
                    break;
                case "indEnd":
                    val = student.getEndInd();
                    break;
                case "groupStart":
                    val = student.getStartGroup();
                    break;
                case "groupEnd":
                    val = student.getEndGroup();
                    break;
                case "checkInStart":
                    val = student.getStartCheckIn();
                    break;
                case "checkInEnd":
                    val = student.getEndCheckIn();
                    break;
                case "walkIns":
                    val = student.getWalkIns();
                    break;
                case "forms":
                    val = student.getForms();
                    break;
                case "notes":
                    val = student.getNotes();
                    break;
                case "hasIEP":
                    val = String.valueOf(student.getIEP());
                    break;
                case "has504":
                    val = String.valueOf(student.get504());
                    break;
                case "hasEval":
                    val = String.valueOf(student.getEval());
                default:
            }
            update = conn.prepareStatement("update clients set "
                    + cat.toString() + " = ? where "
                    + Category.fullName.toString() + " = '"
                    + student.getFullName() + "';");
            update.setString(1, val);
            update.addBatch();
            conn.setAutoCommit(false);
            update.executeBatch();
            conn.setAutoCommit(true);
        } catch (ClassNotFoundException | SQLException sql) {
            sqlErrorAlert(getTrace(sql));
        } finally {
            try {
                if (update != null) {
                    update.close();
                }
            } catch (Exception e) {
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
            }
        }
    }

    private void refreshDatabase(Student student) {
        Connection conn = null;
        PreparedStatement prep = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + schoolPath
                    + File.separator + dbName + ".db");
            prep = conn.prepareStatement("insert into students values"
                    + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
            prep.setString(1, student.getFullName());
            prep.setString(2, student.getFirstName());
            prep.setString(3, student.getLastName());
            prep.setString(4, student.getStartInd());
            prep.setString(5, student.getEndInd());
            prep.setString(6, student.getStartGroup());
            prep.setString(7, student.getEndGroup());
            prep.setString(8, student.getStartCheckIn());
            prep.setString(9, student.getEndCheckIn());
            prep.setString(10, student.getWalkIns());
            prep.setString(11, student.getForms());
            prep.setString(12, student.getNotes());
            prep.setString(13, String.valueOf(student.getIEP()));
            prep.setString(14, String.valueOf(student.get504()));
            prep.setString(15, String.valueOf(student.getEval()));
            prep.addBatch();
            conn.setAutoCommit(false);
            prep.executeBatch();
            conn.setAutoCommit(true);
        } catch (ClassNotFoundException | SQLException sql) {
            sqlErrorAlert(getTrace(sql));
        } finally {
            try {
                if (prep != null) {
                    prep.close();
                }
            } catch (Exception e) {
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
            }
        }

    }

    private List<String> collectArchivedStudents() {
        List<String> list = new ArrayList<>();
        Connection conn = null;
        Statement stat = null;
        ResultSet rs = null;
        try {
            Class.forName("org.sqlite.JDBC");

            conn = DriverManager.getConnection("jdbc:sqlite:"
                    + schoolPath + File.separator + dbName + ".db");

            stat = conn.createStatement();
            rs = stat.executeQuery("select " + Category.fullName.toString()
                    + " from archive;");
            while (rs.next()) {
                list.add(rs.getString("fullName"));
            }
            Collections.sort(list);
        } catch (ClassNotFoundException | SQLException sql) {
            sqlErrorAlert(getTrace(sql));
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (Exception e) {
            }
            try {
                if (stat != null) {
                    stat.close();
                }
            } catch (Exception e) {
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
            }
        }
        return list;
    }

    private List<String> collectCurrentEnrollment(Category startCategory,
            Category endCategory) {
        List<String> list = new ArrayList<>();
        Connection conn = null;
        Statement stat = null;
        ResultSet rs = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:"
                    + schoolPath + File.separator + dbName + ".db");

            stat = conn.createStatement();
            rs = stat.executeQuery("select " + Category.fullName.toString()
                    + " from clients where " + startCategory.toString()
                    + " is not null and " + endCategory.toString()
                    + " is null;");
            while (rs.next()) {
                list.add(rs.getString("fullName"));
            }
            Collections.sort(list);
        } catch (ClassNotFoundException | SQLException sql) {
            sqlErrorAlert(getTrace(sql));
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (Exception e) {
            }
            try {
                if (stat != null) {
                    stat.close();
                }
            } catch (Exception e) {
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
            }
        }
        return list;
    }

    private void transferTableEntry(String fullName, String destination,
            String source) {
        Connection conn = null;
        Statement stat = null;
        ResultSet rs = null;
        PreparedStatement prep = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:"
                    + schoolPath + File.separator + dbName + ".db");
            stat = conn.createStatement();
            rs = stat.executeQuery("select * from " + source
                    + " where " + Category.fullName.toString() + " = '"
                    + fullName + "';");
            prep = conn.prepareStatement("insert into "
                    + destination + " values (?, ?, ?, ?, ?, ?, ?, ?,"
                    + "?, ?, ?, ?, ?, ?, ?);");
            prep.setString(1, rs.getString(Category.fullName.toString()));
            prep.setString(2, rs.getString(Category.firstName.toString()));
            prep.setString(3, rs.getString(Category.lastName.toString()));
            prep.setString(4, rs.getString(Category.indStart.toString()));
            prep.setString(5, rs.getString(Category.indEnd.toString()));
            prep.setString(6, rs.getString(Category.groupStart.toString()));
            prep.setString(7, rs.getString(Category.groupEnd.toString()));
            prep.setString(8, rs.getString(Category.checkInStart.toString()));
            prep.setString(9, rs.getString(Category.checkInEnd.toString()));
            prep.setString(10, rs.getString(Category.walkIns.toString()));
            prep.setString(11, rs.getString(Category.forms.toString()));
            prep.setString(12, rs.getString(Category.notes.toString()));
            prep.setString(13, rs.getString(Category.hasIEP.toString()));
            prep.setString(14, rs.getString(Category.has504.toString()));
            prep.setString(15, rs.getString(Category.hasEval.toString()));
            prep.addBatch();
            conn.setAutoCommit(false);
            prep.executeBatch();
            conn.setAutoCommit(true);
            stat.executeUpdate("delete from " + source + " where "
                    + Category.fullName.toString() + " = '" + fullName
                    + "';");
        } catch (ClassNotFoundException | SQLException sql) {
            sqlErrorAlert(getTrace(sql));
        } finally {
            try {
                if (prep != null) {
                    prep.close();
                }
            } catch (Exception e) {
            }
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (Exception e) {
            }
            try {
                if (stat != null) {
                    stat.close();
                }
            } catch (Exception e) {
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
            }
        }
    }

    private void checkStudent(String student) throws SQLException {
        Connection conn = null;
        Statement stat = null;
        ResultSet rs = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:"
                    + schoolPath + File.separator + dbName + ".db");
            stat = conn.createStatement();
            rs = stat.executeQuery("select * from clients where "
                    + Category.fullName.toString() + " = '" + student + "';");
            String check = rs.getString(Category.fullName.toString());
        } catch (ClassNotFoundException cnf) {
            sqlErrorAlert(getTrace(cnf));
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (Exception e) {
            }
            try {
                if (stat != null) {
                    stat.close();
                }
            } catch (Exception e) {
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
            }
        }
    }

    private void accessArchive(Student student) {
        Connection conn = null;
        Statement stat = null;
        ResultSet rs = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = null;

            conn = DriverManager.getConnection("jdbc:sqlite:"
                    + schoolPath + File.separator + dbName + ".db");
            stat = null;
            rs = null;
            stat = conn.createStatement();
            rs = stat.executeQuery("select * from archive where "
                    + Category.fullName.toString() + " = '"
                    + student.getFullName() + "';");
            student.setStartInd(rs.getString("indStart"));
            student.setEndInd(rs.getString("indEnd"));
            student.setStartGroup(rs.getString("groupStart"));
            student.setEndGroup(rs.getString("groupEnd"));
            student.setStartCheckIn(rs.getString("checkInStart"));
            student.setEndCheckIn(rs.getString("checkInEnd"));
            student.setWalkIns(rs.getString("walkIns"));
            student.setForms(rs.getString("forms"));
            student.setNotes(rs.getString("notes"));
            student.setIEP(Boolean.valueOf(rs.getString("hasIEP")));
            student.set504(Boolean.valueOf(rs.getString("has504")));
            student.setEval(Boolean.valueOf(rs.getString("hasEval")));

        } catch (ClassNotFoundException | SQLException sql) {
            sqlErrorAlert(getTrace(sql));
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (Exception e) {
            }
            try {
                if (stat != null) {
                    stat.close();
                }
            } catch (Exception e) {
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
            }
        }
    }

    private void accessDatabase(Student student) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = null;
            try {
                conn = DriverManager.getConnection("jdbc:sqlite:"
                        + schoolPath + File.separator + dbName + ".db");
                Statement stat = null;
                ResultSet rs = null;
                PreparedStatement prep = null;
                try {
                    stat = conn.createStatement();
                    rs = stat.executeQuery("select * from clients"
                            + " where " + Category.fullName.toString() + " = '"
                            + student.getFullName() + "';");
                    student.setStartInd(rs.getString("indStart"));
                    student.setEndInd(rs.getString("indEnd"));
                    student.setStartGroup(rs.getString("groupStart"));
                    student.setEndGroup(rs.getString("groupEnd"));
                    student.setStartCheckIn(rs.getString("checkInStart"));
                    student.setEndCheckIn(rs.getString("checkInEnd"));
                    student.setWalkIns(rs.getString("walkIns"));
                    student.setForms(rs.getString("forms"));
                    student.setNotes(rs.getString("notes"));
                    student.setIEP(Boolean.valueOf(rs.getString("hasIEP")));
                    student.set504(Boolean.valueOf(rs.getString("has504")));
                    student.setEval(Boolean.valueOf(rs.getString("hasEval")));
                    stat.close();
                    rs.close();
                } catch (SQLException sql) {
                    prep = conn.prepareStatement("insert into"
                            + " clients values (?, ?, ?, ?, ?, ?, ?, ?, ?, "
                            + "?, ?, ?, ?, ?, ?);");
                    prep.setString(1, student.getFullName());
                    prep.setString(2, student.getFirstName());
                    prep.setString(3, student.getLastName());
                    prep.setString(4, student.getStartInd());
                    prep.setString(5, student.getEndInd());
                    prep.setString(6, student.getStartGroup());
                    prep.setString(7, student.getEndGroup());
                    prep.setString(8, student.getStartCheckIn());
                    prep.setString(9, student.getEndCheckIn());
                    prep.setString(10, student.getWalkIns());
                    prep.setString(11, student.getForms());
                    prep.setString(12, student.getNotes());
                    prep.setString(13, String.valueOf(student.getIEP()));
                    prep.setString(14, String.valueOf(student.get504()));
                    prep.setString(15, String.valueOf(student.getEval()));
                    prep.addBatch();
                    conn.setAutoCommit(false);
                    prep.executeBatch();
                    conn.setAutoCommit(true);
                    prep.close();
                } finally {
                    try {
                        if (rs != null) {
                            rs.close();
                        }
                    } catch (Exception e) {
                    }
                    try {
                        if (stat != null) {
                            stat.close();
                        }
                    } catch (Exception e) {
                    }
                    try {
                        if (prep != null) {
                            prep.close();
                        }
                    } catch (Exception e) {
                    }
                }
            } catch (SQLException sql) {
                sqlErrorAlert(getTrace(sql));
            } finally {
                try {
                    if (conn != null) {
                        conn.close();
                    }
                } catch (Exception e) {
                }
            }

        } catch (ClassNotFoundException cnf) {
            sqlErrorAlert(getTrace(cnf));
        }
    }

    public void setFilteredList(ListView<String> listView,
            ObservableList<String> observableList, TextField studentField) {
        FilteredList<String> filteredItems = new FilteredList<>(observableList,
                p -> true);
        studentField.textProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                filteredItems.setPredicate(boxListItem -> {
                    if (boxListItem.toUpperCase().startsWith(newVal.
                            toUpperCase())) {
                        return true;
                    } else {
                        return false;
                    }
                });
            });
        });
        listView.setItems(filteredItems);
    }

    private ListView<String> createVisibleStudentList(
            ObservableList<String> observableStudentList,
            TextField studentField) {
        ListView<String> visibleStudentList = new ListView<>();
        visibleStudentList.setEditable(false);
        visibleStudentList.isVisible();
        if (observableStudentList.isEmpty()) {
            List<String> emptyList = new ArrayList<>();
            emptyList.add("");
            visibleStudentList.setItems(FXCollections
                    .observableList(emptyList));
        }
        setFilteredList(visibleStudentList, observableStudentList,
                studentField);
        setClickEvent(visibleStudentList);
        return visibleStudentList;
    }

    private void setClickEvent(ListView<String> visibleStudentList) {
        visibleStudentList.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2 && !event.isConsumed()) {
                String selectedStudent = visibleStudentList.getSelectionModel()
                        .getSelectedItem().trim();
                if (selectedStudent != null) {
                    Student student = new Student(selectedStudent);
                    File target = new File(studentFilesPath + File.separator
                            + student.getFullName());
                    target.mkdir();
                    accessDatabase(student);
                    initStudentStage(student);
                }
            }
        });
    }

    private void setCheck(CheckBox box, Student student) {
        switch (box.getId()) {
            case "indB":
                if (student.getEndInd() != null) {
                    box.setIndeterminate(true);
                } else if (student.getStartInd() != null) {
                    box.setIndeterminate(false);
                    box.setSelected(true);
                } else {
                    box.setIndeterminate(false);
                    box.setSelected(false);
                }
                break;
            case "groupB":
                if (student.getEndGroup() != null) {
                    box.setIndeterminate(true);
                } else if (student.getStartGroup() != null) {
                    box.setSelected(true);
                } else {
                    box.setSelected(false);
                }
                break;
            case "checkInB":
                if (student.getEndCheckIn() != null) {
                    box.setIndeterminate(true);
                } else if (student.getStartCheckIn() != null) {
                    box.setSelected(true);
                } else {
                    box.setSelected(false);
                }
                break;
        }
    }

    private void checkFieldText(TextField field, Student student)
            throws DataFormatException {
        if (field.getText() == null || field.getText().equals("")) {
        } else if (Pattern.matches("[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9]",
                field.getText())) {
        } else {
            throw new DataFormatException();
        }
    }

    private void addBoxFunction(CheckBox box, Student student, Category cat) {

        String date = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MM-dd-yyyy"));
        TextInputDialog dialog = new TextInputDialog(date);
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
        Button okButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.OK);
        Image okImage = new Image("/studenttracker/Icons/OKButton.png", 55, 33,
                true,
                true);
        okButton.setGraphic(new ImageView(okImage));
        okButton.setText(null);
        Button cancelButton = (Button) dialog.getDialogPane()
                .lookupButton(ButtonType.CANCEL);
        Image cancelImage = new Image(
                "/studenttracker/Icons/CancelButton.png", 55, 33,
                true, true);
        cancelButton.setGraphic(new ImageView(cancelImage));
        cancelButton.setText(null);
        Category catComp = null;
        String area;
        switch (box.getId()) {
            case "indB":
                area = "independent ";
                if (cat == Category.indStart) {
                    catComp = Category.indEnd;
                } else {
                    catComp = Category.indStart;
                }
                break;
            case "groupB":
                area = "group ";
                if (cat == Category.groupStart) {
                    catComp = Category.groupEnd;
                } else {
                    catComp = Category.groupStart;
                }
                break;
            case "checkInB":
                area = "check-in ";
                if (cat == Category.checkInStart) {
                    catComp = Category.checkInEnd;
                } else {
                    catComp = Category.checkInStart;
                }
                break;
            default:
                area = "";
        }
        if (!checkStudentData(cat, student) && !checkStudentData(catComp,
                student)) {
            dialog.setContentText("Set start date for "
                    + student.getFullName() + "'s " + area + "counseling?");

        } else {
            dialog.setContentText("Set end date for"
                    + student.getFullName() + "'s independent counseling?");
        }
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String res = result.get();
            if (Pattern.matches("[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9]",
                    res)) {
                modifyStudent(cat, student, res);
                modifyDatabase(cat, student);
            } else {
                errorAlert("Improper Format", "");
            }
        }
        setCheck(box, student);
    }

    private boolean checkStudentData(Category cat, Student student) {
        String check = null;
        boolean val = false;
        switch (cat.toString()) {
            case "indStart":
                check = student.getStartInd();
                break;
            case "indEnd":
                check = student.getEndInd();
                break;
            case "groupStart":
                check = student.getStartGroup();
                break;
            case "groupEnd":
                check = student.getEndGroup();
                break;
            case "checkInStart":
                check = student.getStartCheckIn();
                break;
            case "checkInEnd":
                check = student.getEndCheckIn();
                break;
            case "walkIns":
                if (!student.getWalkIns().equals("")) {
                    check = student.getWalkIns();
                }
                break;
            case "forms":
                check = student.getForms();
                break;
            case "hasIEP":
                val = student.getIEP();
                break;
            case "has504":
                val = student.get504();
                break;
            case "hasEval":
                val = student.getEval();
        }
        if (check != null) {
            val = true;
        }
        return val;
    }

    private void modifyStudent(Category cat, Student student, String date) {
        switch (cat.toString()) {
            case "indStart":
                student.setStartInd(date);
                break;
            case "indEnd":
                student.setEndInd(date);
                break;
            case "groupStart":
                student.setStartGroup(date);
                break;
            case "groupEnd":
                student.setEndGroup(date);
                break;
            case "checkInStart":
                student.setStartCheckIn(date);
                break;
            case "checkInEnd":
                student.setEndCheckIn(date);
                break;
            case "walkIns":
                student.addWalkIn(date);
                break;
            case "forms":
                student.setForms(date);
                break;
        }
    }

    private String[] formatName(String name) throws DataFormatException {
        String[] splitName = name.split(" ");
        if (splitName.length < 2) {
            throw new DataFormatException();
        }
        String[] formattedName = {splitName[1], splitName[0]};
        if (splitName.length > 2) {
            for (int i = 2; i < splitName.length; i++) {
                formattedName[1] += " " + splitName[i];
            }
        }
        return formattedName;
    }

    private void addStudentToList(List<String> studentList, String name,
            String listPath) {
        try {
            String[] formattedName = formatName(name);
            Boolean check = backgroundList.add(formattedName);
            writeStudentList(listPath);
            readStudentList(studentList, listPath);
        } catch (DataFormatException df) {

        }
    }

    private void removeStudentFromList(List<String> studentList, String name,
            String listPath) {
        try {
            String[] formattedName = formatName(name);
            for (ListIterator<String[]> iter = backgroundList.listIterator();
                    iter.hasNext();) {
                String[] entry = iter.next();
                if (entry[0].equals(formattedName[0]) && entry[1]
                        .equals(formattedName[1])) {
                    iter.remove();
                }
            }
            studentList.remove(name);
            removeFromStudentTable(name.trim());
            writeStudentList(listPath);
        } catch (DataFormatException df) {

        }
    }

    private void writeStudentList(String listPath) {
        List<String> writeList = new ArrayList<>();
        for (int i = 0; i < backgroundList.size(); i++) {
            writeList.add(backgroundList.get(i)[0] + " \t"
                    + backgroundList.get(i)[1]);
        }
        Collections.sort(writeList);
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(listPath)));
            writer.write("Last Name \tFirst Name");
            for (int i = 0; i < writeList.size(); i++) {
                writer.newLine();
                writer.write(writeList.get(i));

            }
            writer.flush();
            writer.close();

        } catch (FileNotFoundException ex) {

        } catch (IOException io) {

        }
    }

    private void readStudentList(List<String> studentList,
            String stdntLstPath) {
        //reads student list from text file.

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(stdntLstPath)));
            String[] colNames = reader.readLine().split("\\t");
            int firstNameInd = 0;
            int lastNameInd = 0;
            for (int i = 0; i < colNames.length; i++) {
                if (colNames[i].matches("First Name")) {
                    firstNameInd = i;
                } else if (colNames[i].matches("Last Name")) {
                    lastNameInd = i;
                }
            }
            String str;
            studentList.clear();
            backgroundList.clear();
            while ((str = reader.readLine()) != null) {
                if (!str.isEmpty()) {
                    String[] array = str.split("\\t");
                    String[] backgroundName = {array[lastNameInd].trim(),
                        array[firstNameInd].trim()};
                    backgroundList.add(backgroundName);
                    studentList.add(array[firstNameInd].trim() + " "
                            + array[lastNameInd].trim());
                }
            }
            reader.close();
        } catch (IOException | NullPointerException e) {
            errorAlert("List Not Found", getTrace(e));
        }
    }

    private void establishGlobalProperties(Properties globalProperties) {
        File globalPropFile = new File(resourcePath + File.separator
                + "global.properties");
        if (globalPropFile.exists()) {
            try {
                globalProperties.load(
                        new FileInputStream(globalPropFile.toString()));
            } catch (IOException io) {
                errorAlert("Global Properties Not Found", getTrace(io));
            }
        } else {
            String schools = "";
            globalProperties.setProperty("schools", schools);
            String selectedSchool = "Default";
            globalProperties.setProperty("selectedSchool", selectedSchool);
            storeGlobalProperties(globalProperties);
        }
    }

    private void establishLocalProperties(Properties localProperties) {
        //Retrieves properties or creates properties file if none exists.

        File localPropFile = new File(schoolPath + File.separator
                + "local.properties");
        if (localPropFile.exists()) {
            try {
                FileInputStream propFileInput = new FileInputStream(
                        localPropFile.toString());
                localProperties.load(propFileInput);
                propFileInput.close();
            } catch (IOException io) {
                errorAlert("Read Local Properties", getTrace(io));
            }
        } else {
            String listPath = "";
            localProperties.setProperty("listPath", listPath);
            storeLocalProperties(localProperties);
        }
    }

    private void storeGlobalProperties(Properties globalProperties) {
        try (FileOutputStream writeProp = new FileOutputStream(
                resourcePath + File.separator + "global.properties")) {
            globalProperties.store(writeProp, null);
        } catch (IOException io) {
            errorAlert("Write Global Properties", getTrace(io));
        }
    }

    private void storeLocalProperties(Properties localProperties) {
        try {
            FileOutputStream writeProp = new FileOutputStream(
                    schoolPath + File.separator + "local.properties");
            localProperties.store(writeProp, null);
            writeProp.close();
        } catch (IOException io) {
            errorAlert("Write Local Properties", getTrace(io));
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copy(File source, File target)
            throws IllegalArgumentException {
        /* copy 
        
         */
        if (!source.toString().equals(target.toString())) {
            try {
                if (target.exists()) {
                    confirmationAlert("File Exists");
                }
                try {
                    FileChannel sourceChannel = new FileInputStream(source)
                            .getChannel();
                    FileChannel targetChannel = new FileOutputStream(target)
                            .getChannel();
                    targetChannel.transferFrom(sourceChannel, 0, sourceChannel
                            .size());
                    targetChannel.close();
                    sourceChannel.close();
                } catch (IOException io) {
                    errorAlert("File Not Found", getTrace(io));
                }
            } catch (IllegalArgumentException ia) {
            }
        }
    }

    private void print(List<String> printList, String printFile, String header) {

        new File(schoolPath + File.separator + "Printed Lists").mkdir();
        try {
            
            BufferedWriter printWriter = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(
                            new File(printFile))));
            printWriter.append(header);
            for (String item : printList) {
                printWriter.newLine();
                printWriter.append(item);
            }
            printWriter.flush();
            printWriter.close();
            Desktop desktop = Desktop.getDesktop();
            desktop.print(new File(printFile));
            Alert printAlert = new Alert(Alert.AlertType.INFORMATION,
                    "Printing List");
            DialogPane dialogPane = printAlert.getDialogPane();
            dialogPane.getStylesheets().add(getClass()
                    .getResource("Main.css").toExternalForm());
            dialogPane.getStyleClass().add("myDialog");
            Button okButton = (Button) printAlert.getDialogPane()
                    .lookupButton(ButtonType.OK);
            Image okImage = new Image("/studenttracker/Icons/OKButton.png", 55, 33,
                    true, true);
            okButton.setGraphic(new ImageView(okImage));
            okButton.setText(null);
            printAlert.showAndWait();

        } catch (IOException io) {

        }
    }

    private void getReadMeText(StringBuilder readMeLines, BufferedReader br)
            throws IOException {
        //Retrieves content from readme file.

        int i = 0;
        String line = "";

        while (line != null) {
            readMeLines.append(line).append("\n");
            line = br.readLine();
        }
    }

}
