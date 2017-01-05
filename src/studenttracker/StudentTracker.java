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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
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
import javafx.stage.Stage;

import static javafx.scene.control.OverrunStyle.LEADING_ELLIPSIS;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import javafx.geometry.Pos;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import static javafx.scene.paint.Color.rgb;

/**
 *
 * @author Andrew Burch
 */
public class StudentTracker extends Application {

    private String schoolName;
    private static final String userPath = System.getProperty("user.dir");
    private static final String resourcePath = userPath + File.separator + "Resources";
    private String schoolPath;
    private String studentFilesPath;
    private static final String dbName = "students";
    private List<String[]> backgroundList = new ArrayList<>();

    enum Category {
        fullName, firstName, lastName, indStart, indEnd, groupStart, groupEnd,
        checkInStart, checkInEnd, walkIns, forms, notes, hasIEP, has504, hasEval
    }

    @Override

    public void start(Stage primaryStage) {
        //Set class variables excluding selectedStudent.

        new File(resourcePath).mkdir();
        Properties globalProperties = new Properties();
        establishGlobalProperties(globalProperties);

        schoolPath = resourcePath + File.separator + globalProperties.getProperty("selectedSchool");
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
        final Label listLabel = new Label("Student Name:");
        final TextField studentField = new TextField();
        final ListView<String> visibleStudentList = createVisibleStudentList(
                observableStudentList, studentField);
        final Button setDirBtn = initDirectoryButton(visibleStudentList,
                observableStudentList, studentList, studentField,
                listStatusText, localProperties, listStatusTextProperty,
                primaryStage);
        final Label studentListText = new Label("Student List:");
        final Button viewIndButton = initButton("ViewIndividualListButton.png", 110, 66);
        viewIndButton.setOnMouseClicked((MouseEvent event) -> {
            List<String> indList = collectCurrentEnrollment(Category.indStart,
                    Category.indEnd);
            initClientStage(indList, "Individual");
        });
        final Button viewGroupButton = initButton("ViewGroupListButton.png", 110, 66);
        viewGroupButton.setOnMouseClicked((MouseEvent event) -> {
            List<String> groupList = collectCurrentEnrollment(Category.groupStart,
                    Category.groupEnd);
            initClientStage(groupList, "Group");
        });
        final Button viewArchiveButton = initViewArchiveButton(visibleStudentList,
                observableStudentList, studentList, listStatusTextProperty,
                localProperties, studentField);
        final Button archiveStudentButton = initButton("ArchiveStudentButton.png", 110, 66);
        archiveStudentButton.setOnAction((ActionEvent event) -> {
            confirmationAlert("Archive");
            final String selectedStudent = visibleStudentList.getSelectionModel()
                    .getSelectedItem().trim();
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
            transferTableEntry(selectedStudent, "archive", "clients");
            removeStudentFromList(studentList, selectedStudent,
                    localProperties.getProperty("listPath"));
            setFilteredList(visibleStudentList, observableStudentList,
                    studentField);
        });
        final Button addStudentButton = initButton("AddStudentButton.png", 110, 66);
        addStudentButton.setOnAction((ActionEvent event) -> {
            initAddStudentStage(visibleStudentList, observableStudentList,
                    studentList, studentField, listStatusTextProperty,
                    localProperties);
        });
        final Button removeStudentButton = initRemoveStudentButton(localProperties,
                listStatusTextProperty, visibleStudentList,
                observableStudentList, studentList, studentField);
        final MenuBar menuBar = createMenu(visibleStudentList, observableStudentList,
                studentList, listStatusTextProperty, globalProperties,
                localProperties, primaryStage, listStatusText, studentField);
        final Scene scene = new Scene(primaryStageLayout(schoolLabel, activeSchool,
                listStatusText, listLabel, studentField, visibleStudentList,
                setDirBtn, studentListText, viewIndButton, viewGroupButton,
                viewArchiveButton, addStudentButton, removeStudentButton,
                archiveStudentButton, menuBar));
        scene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        primaryStage.setTitle("Student Tracker");
        primaryStage.setResizable(false);
        primaryStage.setScene(scene);
        primaryStage.show();
        if (listPath.isEmpty()) {
            System.out.println("Setting text fill to red!");
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

    public static void main(String[] args) {
        Application.launch(args);
    }

    private void initClientStage(List<String> clientList, String group) {
        final ListView<String> visibleGroupList = new ListView(FXCollections
                .observableList(clientList));

        setClickEvent(visibleGroupList);
        Button printButton = initPrintButton(clientList, group);
        Button okButton = initButton("OKButton.png", 55, 33);
        Stage clientStage = new Stage();
        okButton.setOnAction((ActionEvent event) -> {
            clientStage.close();
        });
        clientStage.setTitle("Students in " + group + " Counseling");
        clientStage.initModality(Modality.APPLICATION_MODAL);
        HBox buttonBox = new HBox();
        buttonBox.getChildren().addAll(okButton, printButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getStyleClass().setAll("box");
        VBox individualStageLayout = new VBox();
        individualStageLayout.getStyleClass().setAll("layout-box");
        individualStageLayout.getChildren().addAll(visibleGroupList, buttonBox);
        Scene indScene = new Scene(individualStageLayout);
        indScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        clientStage.setScene(indScene);
        clientStage.show();
    }

    private Button initPrintButton(List<String> printList, String group) {
        Button printButton = initButton("PrintListButton.png", 110, 66);
        String fileDate = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String printDate = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MMM d, yyyy"));
        String printTime = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("h:mm a"));
        printButton.setOnAction((ActionEvent event) -> {
            new File(schoolPath + File.separator + "Printed Lists").mkdir();
            try {
                String printFile = schoolPath + File.separator + "Printed Lists"
                        + File.separator + fileDate + " " + group + " List.txt";
                BufferedWriter printWriter = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(
                                new File(printFile))));
                printWriter.append(group + " counseling client list created "
                        + printDate + " at " + printTime + ":");
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
                printAlert.showAndWait();

            } catch (IOException io) {

            }

        });
        return printButton;
    }

    private void initArchiveStage(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty listStatusTextProperty,
            Properties localProperties, TextField studentField) {
        List<String> archivedStudents = collectArchivedStudents();
        ListView<String> archiveList = new ListView<>(FXCollections
                .observableList(archivedStudents));
        Button returnStudentButton = initButton("ReturnStudentButton.png", 110,
                66);
        returnStudentButton.setOnAction((ActionEvent event) -> {
            String selectedStudent = archiveList.getSelectionModel()
                    .getSelectedItem().trim();
            if (selectedStudent != null) {
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
            }
        });
        Button printButton = initPrintButton(archivedStudents, "Archive");
        HBox buttonBox = new HBox();
        buttonBox.getChildren().addAll(returnStudentButton, printButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getStyleClass().setAll("box");
        VBox archiveStageLayout = new VBox();
        archiveStageLayout.getChildren().addAll(archiveList, buttonBox);
        archiveStageLayout.getStyleClass().setAll("layout-box");
        Stage individualStage = new Stage();
        individualStage.setTitle("Archived Students");
        individualStage.initModality(Modality.APPLICATION_MODAL);
        Scene indScene = new Scene(archiveStageLayout);
        indScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        individualStage.setScene(indScene);
        individualStage.show();
    }

    private MenuBar createMenu(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty listStatusTextProperty,
            Properties globalProperties, Properties localProperties,
            Stage primaryStage, Label listStatusText, TextField studentField) {
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        Menu changeSchool = new Menu("Change School");
        Menu assignDatabase = new Menu("Assign Database to...");
        Menu removeSchool = new Menu("Remove School");
        updateMenus(visibleStudentList, observableStudentList,
                studentList, listStatusTextProperty, globalProperties,
                localProperties, primaryStage, changeSchool, removeSchool,
                assignDatabase, listStatusText, studentField);
        MenuItem addSchool = buildAddSchoolMenu(visibleStudentList,
                observableStudentList, studentList, listStatusTextProperty,
                globalProperties, localProperties, primaryStage, changeSchool,
                removeSchool, assignDatabase, listStatusText, studentField);
        MenuItem readMeMenu = buildReadMeMenuItem(primaryStage);
        MenuItem closeItem = buildCloseMenuItem();
        fileMenu.getItems().addAll(changeSchool, addSchool,
                removeSchool, assignDatabase, readMeMenu, closeItem);
        menuBar.getMenus().add(fileMenu);
        return menuBar;
    }

    private void updateMenus(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty listStatusTextProperty,
            Properties globalProperties, Properties localProperties,
            Stage primaryStage, Menu changeSchool, Menu removeSchool,
            Menu assignDatabase, Label listStatusText, TextField studentField) {
        changeSchool.getItems().clear();
        removeSchool.getItems().clear();
        assignDatabase.getItems().clear();
        List<String> schools = Arrays.asList(globalProperties.getProperty("schools")
                .split(";"));
        Collections.sort(schools);
        System.out.println("Size of school list: " + schools.size());
        if (!schools.get(0).equals("")) {
            for (String school : schools) {
                MenuItem changeSchoolItem = new MenuItem(school);
                changeSchoolItem.setOnAction((ActionEvent event) -> {
                    if (!school.equals(globalProperties
                            .getProperty("selectedSchool"))) {
                        globalProperties.setProperty("selectedSchool", school);
                        schoolPath = resourcePath + File.separator + school;
                        studentFilesPath = schoolPath + File.separator + "Student Files";
                        storeGlobalProperties(globalProperties);
                        establishLocalProperties(localProperties);
                        checkDatabase();
                        if (!localProperties.getProperty("listPath").isEmpty()) {
                            readStudentList(studentList, localProperties.getProperty("listPath"));
                            System.out.println("Setting text fill to white in updateMenus!");
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
                        System.err.println("Cannot switch to school in use");
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
                                listStatusTextProperty, primaryStage,
                                changeSchool, removeSchool, assignDatabase,
                                studentField, listStatusText);
                    } else {
                        System.err.println("Cannot delete database currently in use");
                    }
                });
                removeSchool.getItems().add(removeSchoolItem);
                MenuItem assignDatabaseItem = new MenuItem(school);
                assignDatabaseItem.setOnAction((ActionEvent event) -> {
                    System.out.println("Renaming " + schoolPath + " to "
                            + school);
                    if (new File(resourcePath + File.separator + school)
                            .exists()) {
                        try {
                            confirmationAlert("File Exists");
                            try {
                                File deleteSchoolPath = new File(schoolPath);
                                String deleteSchool = deleteSchoolPath.getName();
                                System.out.println("deleteSchool is " + deleteSchool);
                                String[] splitSchoolList = globalProperties.getProperty("schools").split(";");
                                System.out.println("splitSchoolList has " + splitSchoolList.length + " entries.");
                                List<String> schoolPropertyList = Arrays.asList(splitSchoolList);
                                List<String> schoolList = new ArrayList<>(schoolPropertyList);
                                schoolList.remove(deleteSchool);
                                updateGlobalPropertiesFromList(globalProperties, schoolList);
                                deleteDirectory(Paths.get(resourcePath
                                        + File.separator + school));
                                Boolean check = new File(schoolPath)
                                        .renameTo(new File(resourcePath
                                                + File.separator + school));
                                System.out.println(check);
                                System.out.println("Things are still happening");
                                schoolPath = resourcePath + File.separator + school;
                                studentFilesPath = schoolPath + File.separator + "Student Files";
                                System.out.println("Paths are set");
                                File listFile = new File(localProperties.getProperty("listPath"));
                                System.out.println("made list file");
                                if (!listFile.getName().isEmpty()) {
                                    localProperties.setProperty("listPath",
                                            schoolPath + File.separator
                                            + listFile.getName());
                                }
                                System.out.println("localProperty changed");
                                System.out.println("local list path set to: " + localProperties.getProperty("listPath"));
                                storeLocalProperties(localProperties);
                                globalProperties.setProperty("selectedSchool", school);
                                System.out.println("set selectedSchool to " + school);
                                System.out.println("schools are: " + globalProperties.getProperty("schools"));
                                updateMenus(visibleStudentList, observableStudentList,
                                        studentList, listStatusTextProperty, globalProperties,
                                        localProperties, primaryStage, changeSchool, removeSchool,
                                        assignDatabase, listStatusText, studentField);
                            } catch (IOException io) {
                                System.err.println("Could not delete");
                            }
                        } catch (IllegalArgumentException ia) {
                        }
                    }
                });
                assignDatabase.getItems().add(assignDatabaseItem);
            }
        }
    }

    private void initRemoveSchoolStage(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, List<String> schools, String school,
            Properties globalProperties, Properties localProperties,
            StringProperty listStatusTextProperty, Stage primaryStage,
            Menu changeSchool, Menu removeSchool, Menu assignDatabase,
            TextField studentField, Label listStatusText) {
        Stage removeSchoolStage = new Stage();
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
                System.out.println(school + " removed? " + check);
                updateGlobalPropertiesFromList(globalProperties, removeList);
                updateMenus(visibleStudentList,
                        observableStudentList, studentList,
                        listStatusTextProperty, globalProperties,
                        localProperties, primaryStage, changeSchool,
                        removeSchool, assignDatabase, listStatusText,
                        studentField);
                storeGlobalProperties(globalProperties);

            } catch (IOException io) {
                System.err.println("Could not delete directory");
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

    private void updateGlobalPropertiesFromList(Properties globalProperties,
            List<String> list) {
        for (int i = 0; i < list.size(); i++) {
            System.out.println(list.get(i) + " remains.");
        }
        if (!list.isEmpty()) {
            StringBuilder schoolSB = new StringBuilder(list
                    .get(0));
            for (int i = 1; i < list.size(); i++) {
                schoolSB.append(";");
                schoolSB.append(list.get(i));
            }
            globalProperties.setProperty("schools",
                    schoolSB.toString());
            System.out.println("schools set to:" + globalProperties
                    .getProperty("schools"));
        } else {
            globalProperties.setProperty("schools", "");
            System.out.println("schools set to:" + globalProperties
                    .getProperty("schools"));
        }
    }

    private VBox removeSchoolStageLayout(Label removeText, Button okButton,
            Button cancelButton) {
        HBox buttons = new HBox();
        buttons.getStyleClass().setAll("box");
        buttons.getChildren().addAll(okButton, cancelButton);
        VBox layout = new VBox();

        layout.setId("layout");
        layout.getChildren().addAll(removeText, buttons);
        return layout;
    }

    private MenuItem buildAddSchoolMenu(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty listStatusTextProperty,
            Properties globalProperties, Properties localProperties,
            Stage primaryStage, Menu changeSchool, Menu removeSchool,
            Menu assignDatabase, Label listStatusText, TextField studentField) {
        MenuItem addSchool = new MenuItem("Add School");
        addSchool.setOnAction((ActionEvent event) -> {
            initAddSchoolStage(visibleStudentList, observableStudentList,
                    studentList, listStatusTextProperty, globalProperties,
                    localProperties, primaryStage, changeSchool, removeSchool,
                    assignDatabase, listStatusText, studentField);
        });
        return addSchool;
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

    private void checkDatabase() {
        File file = new File(schoolPath + File.separator + File.separator
                + dbName + ".db");
//        if (!file.exists()) {
            try {
                Class.forName("org.sqlite.JDBC");
                try {
                    Connection conn = DriverManager.getConnection("jdbc:sqlite:"
                            + schoolPath + File.separator + dbName + ".db");
                    Statement stat = conn.createStatement();
                    try {
                        stat.executeUpdate("drop table if exists clients;");
                        stat.executeUpdate("drop table if exists archive;");
                        System.out.println("Dropping students table");
                    } catch (SQLException sql) {
                        sqlErrorAlert(getTrace(sql));
                    }
                    try {
                        stat.executeUpdate("create table clients (fullName, "
                                + "firstName, lastName, indStart, indEnd, "
                                + "groupStart, groupEnd, checkInStart, checkInEnd, "
                                + "walkIns, forms, notes, hasIEP, has504,"
                                + " hasEval);");
                        stat.executeUpdate("create table archive (fullName, "
                                + "firstName, lastName, indStart, indEnd, "
                                + "groupStart, groupEnd, checkInStart, checkInEnd, "
                                + "walkIns, forms, notes, hasIEP, has504,"
                                + " hasEval);");
                    } catch (SQLException sql) {
                        sqlErrorAlert(getTrace(sql));
                    } finally {
                        stat.close();
                        conn.close();
                    }
                } catch (SQLException sql) {
                    sqlErrorAlert(getTrace(sql));

                }
            } catch (ClassNotFoundException cnf) {
                sqlErrorAlert(getTrace(cnf));
            }
//        }
    }

    private String getTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private void removeFromStudentTable(String name) {
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn = DriverManager.getConnection("jdbc:sqlite:"
                        + schoolPath + File.separator + dbName + ".db");
                Statement stat = conn.createStatement();
                try {
                    stat.executeUpdate("delete from clients where fullName = '"
                            + name + "';");
                } catch (SQLException sql) {
                    sqlErrorAlert(getTrace(sql));
                } finally {
                    conn.close();
                }
            } catch (SQLException sql) {
                sqlErrorAlert(getTrace(sql));
            }
        } catch (ClassNotFoundException cnf) {
            sqlErrorAlert(getTrace(cnf));
        }
    }

    private void modifyDatabase(Category cat, Student student) {
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn = DriverManager.getConnection("jdbc:sqlite:"
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
                try {
                    PreparedStatement update = conn.prepareStatement("update "
                            + "clients set " + cat.toString() + " = '" + val
                            + "' where fullName = '" + student.getFullName()
                            + "';");
                    update.addBatch();
                    conn.setAutoCommit(false);
                    update.executeBatch();
                    conn.setAutoCommit(true);
                    update.close();
                } catch (SQLException sql) {
                    sqlErrorAlert(getTrace(sql));
                }
                conn.close();
            } catch (SQLException sql) {
                sqlErrorAlert(getTrace(sql));
            }
        } catch (ClassNotFoundException cnf) {
            sqlErrorAlert(getTrace(cnf));
        }
    }

    private void refreshDatabase(Student student) {
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn = DriverManager.getConnection("jdbc:sqlite:"
                        + schoolPath + File.separator + dbName + ".db");
                PreparedStatement prep = conn.prepareStatement("insert into"
                        + " students values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?);");
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
                conn.close();
            } catch (SQLException sql) {
                sqlErrorAlert(getTrace(sql));

            }
        } catch (ClassNotFoundException cnf) {
            sqlErrorAlert(getTrace(cnf));
        }
    }
    
    private List<String> collectArchivedStudents() {
        List<String> list = new ArrayList<>();
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn = DriverManager.getConnection("jdbc:sqlite:"
                        + schoolPath + File.separator + dbName + ".db");
                try {
                    Statement stat = conn.createStatement();
                    ResultSet rs = stat.executeQuery("select "
                            + Category.fullName.toString() + " from archive;");
                    while (rs.next()) {
                        list.add(rs.getString("fullName"));
                    }
                    rs.close();
                    stat.close();
                    Collections.sort(list);
                } catch (SQLException sql) {
                    sqlErrorAlert(getTrace(sql));
                }
                conn.close();
            } catch (SQLException sql) {
                sqlErrorAlert(getTrace(sql));
            }

        } catch (ClassNotFoundException cnf) {
            sqlErrorAlert(getTrace(cnf));
        }
        return list;
    }

    private List<String> collectCurrentEnrollment(Category startCategory,
            Category endCategory) {
        List<String> list = new ArrayList<>();
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn = DriverManager.getConnection("jdbc:sqlite:"
                        + schoolPath + File.separator + dbName + ".db");
                try {
                    Statement stat = conn.createStatement();
                    ResultSet rs = stat.executeQuery("select "
                            + Category.fullName.toString() + " from clients where "
                            + startCategory.toString() + " is not null and "
                            + endCategory.toString() + " is null;");
                    while (rs.next()) {
                        list.add(rs.getString("fullName"));
                    }
                    rs.close();
                    stat.close();
                    Collections.sort(list);
                } catch (SQLException sql) {
                    sqlErrorAlert(getTrace(sql));
                }
                conn.close();
            } catch (SQLException sql) {
                sqlErrorAlert(getTrace(sql));
            }

        } catch (ClassNotFoundException cnf) {
            sqlErrorAlert(getTrace(cnf));
        }
        return list;
    }

    private void transferTableEntry(String fullName, String destination, String source) {
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn = DriverManager.getConnection("jdbc:sqlite:"
                        + schoolPath + File.separator + dbName + ".db");
                
                try {
                    Statement stat = conn.createStatement();
                    System.out.println("Transferring " + fullName);
                    ResultSet rs = stat.executeQuery("select * from " + source
                            + " where " + Category.fullName.toString() + " = '"
                            + fullName + "';");
                    PreparedStatement prep = conn.prepareStatement("insert into "
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
                    rs.close();
                    prep.close();
                } catch (SQLException sql) {
                    sqlErrorAlert(getTrace(sql));
                }
                conn.close();
            } catch (SQLException sql) {
                sqlErrorAlert(getTrace(sql));
            }

        } catch (ClassNotFoundException cnf) {
            sqlErrorAlert(getTrace(cnf));
        }
    }

    private void accessDatabase(Student student) {
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn = DriverManager.getConnection("jdbc:sqlite:"
                        + schoolPath + File.separator + dbName + ".db");
                try {
                    Statement stat = conn.createStatement();
                    ResultSet rs = stat.executeQuery("select * from clients "
                            + "where fullName = '" + student.getFullName()
                            + "';");
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
                    PreparedStatement prep = conn.prepareStatement("insert into"
                            + " clients values (?, ?, ?, ?, ?, ?, ?, ?, ?, "
                            + "?, ?, ?, ?, ?, ?);");
                    prep.setString(1, student.getFullName());
                    prep.setString(2, student.getFirstName());
                    prep.setString(3, student.getLastName());
                    prep.setString(4, student.getStartInd());
                    prep.setString(5, student.getEndInd());
                    System.out.println("Writing '" + student.getEndInd() + "' to indEnd in database");
                    prep.setString(6, student.getStartGroup());
                    prep.setString(7, student.getEndGroup());
                    prep.setString(8, student.getStartCheckIn());
                    prep.setString(9, student.getEndCheckIn());
                    prep.setString(10, student.getWalkIns());
                    prep.setString(11, student.getForms());
                    prep.setString(12, String.valueOf(student.getNotes()));
                    prep.setString(13, String.valueOf(student.getIEP()));
                    prep.setString(14, String.valueOf(student.get504()));
                    prep.setString(15, String.valueOf(student.getEval()));
                    prep.addBatch();
                    conn.setAutoCommit(false);
                    prep.executeBatch();
                    conn.setAutoCommit(true);
                    prep.close();
                }
                conn.close();
            } catch (SQLException sql) {
                sqlErrorAlert(getTrace(sql));
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
            visibleStudentList.setItems(FXCollections.observableList(emptyList));
        }
        setFilteredList(visibleStudentList, observableStudentList, studentField);
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

    private void initStudentStage(Student student) {
        String studentName = student.getFullName();
        Stage studentStage = new Stage();
        studentStage.initModality(Modality.APPLICATION_MODAL);
        studentStage.setTitle("Counseling");
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
        modDate.setId("modDate");
        modDate.setOnMouseClicked((MouseEvent event) -> {
            modifyStudentDialog(student, indBox, groupBox, checkInBox);
        });
        Button addWalkInButton = createAddWalkInButton(student);
        Button detailsButton = createDetailsButton(student);
        Label title = new Label("Counseling enrollment for " + studentName);
        Scene studentScene = new Scene(studentStageLayout(indBox, groupBox,
                checkInBox, modDate, addWalkInButton, detailsButton, title));
        studentScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        studentStage.setScene(studentScene);
        studentStage.show();
    }

    private VBox studentStageLayout(CheckBox indBox, CheckBox groupBox,
            CheckBox checkInBox, Label modDate, Button addWalkInButton,
            Button detailsButton, Label title) {
        VBox studentBox = new VBox();
        HBox buttons = new HBox();
        buttons.getStyleClass().setAll("box");
        buttons.getChildren().addAll(addWalkInButton, detailsButton);
        buttons.setAlignment(Pos.CENTER);
        studentBox.getChildren().addAll(title, indBox, groupBox, checkInBox,
                modDate);
        VBox contentBox = new VBox();
        contentBox.setId("layout");
        contentBox.getChildren().addAll(studentBox, buttons);
        return contentBox;
    }

    private Button createDetailsButton(Student student) {
        Button detailsButton = initButton("DetailsButton.png", 110, 66);
        detailsButton.setOnAction((ActionEvent event) -> {
            initDetailsStage(student);
        });
        return detailsButton;
    }

    private void initDetailsStage(Student student) {
        Stage detailsStage = new Stage();
        detailsStage.setTitle("Student Details");
        detailsStage.initModality(Modality.APPLICATION_MODAL);
        CheckBox IEPBox = new CheckBox("IEP");
        if (student.getIEP()) {
            IEPBox.setSelected(true);
        }
        IEPBox.setOnAction((ActionEvent event) -> {
            student.toggleIEP();
            modifyDatabase(Category.hasIEP, student);
            IEPBox.setSelected(student.getIEP());
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

        Scene detailsScene = new Scene(detailsStageLayout(IEPBox, ffBox,
                evalBox, formText, visibleFormList,
                addFormButton, removeFormButton, noteText,
                noteArea, okButton, cancelButton), 300, 500);
        detailsScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        detailsStage.setScene(detailsScene);
        detailsStage.show();
    }

    private VBox detailsStageLayout(CheckBox IEPBox, CheckBox ffBox,
            CheckBox evalBox, Label formText, ListView<String> visibleFormList,
            Button addFormButton, Button removeFormButton, Label noteText,
            TextArea noteArea, Button okButton, Button cancelButton) {
        HBox formButtons = new HBox();
        formButtons.getStyleClass().setAll("box");
        formButtons.getChildren().addAll(addFormButton, removeFormButton);
        VBox noteLayout = new VBox();
        noteLayout.getChildren().addAll(noteText, noteArea);
        VBox formLayout = new VBox();
        formLayout.getChildren().addAll(formText, visibleFormList);
        VBox checkBoxLayout = new VBox();
        checkBoxLayout.getChildren().addAll(IEPBox, ffBox, evalBox);
        HBox exitButtons = new HBox();
        exitButtons.getChildren().addAll(okButton, cancelButton);
        exitButtons.getStyleClass().setAll("box");
        exitButtons.setId("ok-button");
        VBox stageLayout = new VBox();
        stageLayout.setId("layout");
        stageLayout.getChildren().addAll(checkBoxLayout, formLayout,
                formButtons, noteLayout, exitButtons);
        return stageLayout;
    }

    private Button initAddFormButton(Student student, Stage parentStage,
            List forms, ListView visibleList) {
        Button addFormButton = initButton("AddFormButton.png", 110, 66);
        addFormButton.setOnAction((ActionEvent event) -> {
            String studentPath = studentFilesPath + File.separator
                    + student.getFullName();
            FileChooser chooseList = new FileChooser();
            chooseList.setInitialDirectory(new File(studentPath));
            chooseList.setTitle("Select Form File");
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
        modStudentStage.setTitle("Modify Dates");
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
                errorAlert("Improper Format");
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
        buttonBox.setId("ok-button");
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
            dialog.setContentText("Set start date for"
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
                errorAlert("Improper Format");
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

    private void errorAlert(String error) {
        String alertText = null;
        switch (error) {
            case "Improper Format":
                alertText = "Specified date was not formatted properly."
                        + " Please use \"mm-dd-yyyy\" format.";
                break;
            case "List Not Found":
                alertText = "Specified student list was not found. "
                        + "Please choose a different list.";
                break;
        }
        Alert errAlert = new Alert(Alert.AlertType.ERROR, alertText);
        DialogPane dialogPane = errAlert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
        dialogPane.getStyleClass().add("myDialog");
        Button okButton = (Button) errAlert.getDialogPane().lookupButton(ButtonType.OK);
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
        }
        Alert confAlert = new Alert(Alert.AlertType.CONFIRMATION, alertText);
        DialogPane dialogPane = confAlert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
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
        dialogPane.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
        Button okButton = (Button) sqlErrorAlert.getDialogPane()
                .lookupButton(ButtonType.OK);
        Image okImage = new Image("/studenttracker/Icons/OKButton.png", 55, 33,
                true, true);
        okButton.setGraphic(new ImageView(okImage));
        sqlErrorAlert.showAndWait();
    }

    private void printErrorAlert(String trace) {
        Alert printErrorAlert = new Alert(Alert.AlertType.ERROR,
                "Error printing document:\n" + trace);
        DialogPane dialogPane = printErrorAlert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
        Button okButton = (Button) printErrorAlert.getDialogPane()
                .lookupButton(ButtonType.OK);
        Image okImage = new Image("/studenttracker/Icons/OKButton.png", 55, 33,
                true, true);
        okButton.setGraphic(new ImageView(okImage));
        printErrorAlert.showAndWait();
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
        addStudentStage.setTitle("Add Student");
        addStudentStage.initModality(Modality.APPLICATION_MODAL);
        Label addText = new Label("Type the name of the student you would like "
                + "to add and press OK");
        TextField addField = new TextField();
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            String fieldName = addField.getText();
            if (fieldName != null && !fieldName.equals("")) {
                String[] fileName = new File(
                        localProperties.getProperty("listPath")).getName().split(" ");
                if (!fileName[0].equals("Modified")) {
                    localProperties.setProperty("listPath", resourcePath + File.separator
                            + "Modified " + new File(
                                    localProperties.getProperty("listPath")).getName());
                    storeLocalProperties(localProperties);
                    setListStatusTextProperty(localProperties.getProperty("listPath"),
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
        addScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        addStudentStage.setScene(addScene);
        addStudentStage.show();
    }

    private void initAddSchoolStage(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, StringProperty listStatusTextProperty,
            Properties globalProperties, Properties localProperties,
            Stage primaryStage, Menu changeSchool, Menu removeSchool,
            Menu assignDatabase, Label listStatusText, TextField studentField) {
        Stage addSchoolStage = new Stage();
        addSchoolStage.setTitle("Add School");
        addSchoolStage.initModality(Modality.APPLICATION_MODAL);
        Label addText = new Label("Type the name of the school you would like "
                + "to add and press OK");
        TextField addField = new TextField();
        Button okButton = initButton("OKButton.png", 55, 33);
        okButton.setOnAction((ActionEvent event) -> {
            String fieldName = addField.getText();
            if (fieldName != null && !fieldName.equals("")) {
                System.out.println("schools property:" + globalProperties.getProperty("schools"));
                if (globalProperties.getProperty("schools").equals("")) {
                    globalProperties.setProperty("schools", fieldName);
                } else {
                    globalProperties.setProperty("schools",
                            globalProperties.getProperty("schools") + ";"
                            + fieldName);
                }
                System.out.println("Selected school is " + globalProperties.getProperty("selectedSchool"));
                storeGlobalProperties(globalProperties);

                schoolPath = resourcePath + File.separator
                        + fieldName;
                System.out.println("Selected school is " + fieldName);
                new File(schoolPath).mkdir();
                new File(schoolPath + File.separator + "students").mkdir();
                checkDatabase();
                Properties newLocalProperties = new Properties();
                establishLocalProperties(newLocalProperties);
                schoolPath = resourcePath + File.separator + globalProperties.getProperty("selectedSchool");
                System.out.println("Selected school is " + globalProperties.getProperty("selectedSchool"));
                updateMenus(visibleStudentList, observableStudentList,
                        studentList, listStatusTextProperty, globalProperties,
                        localProperties, primaryStage, changeSchool, removeSchool,
                        assignDatabase, listStatusText, studentField);
                addSchoolStage.close();
            }
        });
        Button cancelButton = initButton("CancelButton.png", 55, 33);
        cancelButton.setOnAction((ActionEvent event) -> {
            addSchoolStage.close();
        });

        Scene addScene = new Scene(addStudentStageLayout(addText, addField,
                okButton, cancelButton));
        addScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        addSchoolStage.setScene(addScene);
        addSchoolStage.show();
    }

    private VBox addStudentStageLayout(Label addText,
            TextField addField, Button okButton, Button cancelButton) {
        HBox buttons = new HBox();
        buttons.getChildren().addAll(okButton, cancelButton);
        buttons.getStyleClass().setAll("box");
        buttons.setId("ok-button");
        VBox addBox = new VBox();
        addBox.getChildren().addAll(addText, addField);
        VBox stageLayout = new VBox();
        stageLayout.setId("layout");
        stageLayout.getChildren().addAll(addBox, buttons);
        return stageLayout;
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
                if (entry[0].equals(formattedName[0]) && entry[1].equals(formattedName[1])) {
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
                String[] array = str.split("\\t");
                String[] backgroundName = {array[lastNameInd].trim(),
                    array[firstNameInd].trim()};
                backgroundList.add(backgroundName);
                studentList.add(array[firstNameInd].trim() + " "
                        + array[lastNameInd].trim());
            }
            reader.close();
        } catch (IOException io) {
            System.err.println("I/O Exception in method readStudentList: "
                    + io);
            errorAlert("List Not Found");
        } catch (NullPointerException npe) {
            errorAlert("List Not Found");
        }
    }

    private void establishGlobalProperties(Properties globalProperties) {
        System.out.println("Establishing global properties");
        File globalPropFile = new File(resourcePath + File.separator + "global.properties");
        if (globalPropFile.exists()) {
            System.out.println("Found global properties");
            try {
                globalProperties.load(
                        new FileInputStream(globalPropFile.toString()));
                System.out.println("Loaded global properties:");

            } catch (IOException io) {
                System.err.println("Unable to load global properties file");
            }
        } else {
            System.out.println("Properties not found, setting defaults");
            String schools = "";
            globalProperties.setProperty("schools", schools);
            String selectedSchool = "Default";
            globalProperties.setProperty("selectedSchool", selectedSchool);
            storeGlobalProperties(globalProperties);
        }
        System.out.println("schools: " + globalProperties.getProperty("schools"));
        System.out.println("selected school: " + globalProperties.getProperty("selectedSchool"));
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

    private void establishLocalProperties(Properties localProperties) {
        //Retrieves properties or creates properties file if none exists.

        System.out.println("Establishing local properties");
        File localPropFile = new File(schoolPath + File.separator
                + "local.properties");
        if (localPropFile.exists()) {
            System.out.println("Found local properties file.");
            try {
                FileInputStream propFileInput = new FileInputStream(
                        localPropFile.toString());
                localProperties.load(propFileInput);
                System.out.println("Loaded properties:");
                propFileInput.close();
                System.out.println("list path: " + localProperties.getProperty("listPath"));
            } catch (IOException io) {
                System.err.println("Unable to load local properties file");
            }
        } else {
            System.out.println("Could not find local properties file.");
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
            System.err.println("Could not write properties to file: " + io);
        }
    }

    private void storeLocalProperties(Properties localProperties) {
        try {
            FileOutputStream writeProp = new FileOutputStream(
                    schoolPath + File.separator + "local.properties");
            localProperties.store(writeProp, null);
            writeProp.close();
        } catch (IOException io) {
            System.err.println("Could not write properties to file: " + io);
        }
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
                FileChannel sourceChannel = new FileInputStream(source)
                        .getChannel();
                FileChannel targetChannel = new FileOutputStream(target)
                        .getChannel();
                targetChannel.transferFrom(sourceChannel, 0, sourceChannel
                        .size());
                targetChannel.close();
                sourceChannel.close();
            } catch (IOException io) {
                System.err.println("Method copy unable to generate file "
                        + "channels!");
            }
        }
    }

    private VBox primaryStageLayout(Label schoolLabel, Label activeSchool,
            Label listStatusText, Label listLabel, TextField studentField,
            ListView<String> visibleStudentList, Button setDirBtn,
            Label studentListText, Button viewIndButton, Button viewGroupButton,
            Button viewArchiveButton, Button addStudentButton,
            Button removeStudentButton, Button archiveStudentButton,
            MenuBar menuBar) {
        //Creates GUI elements and layout.
        
        VBox studentName = new VBox();
        studentName.getChildren().addAll(studentListText, studentField);
        studentName.setAlignment(Pos.CENTER_LEFT);
        VBox school = new VBox();
        activeSchool.setId("activeSchool-label");
        school.getChildren().addAll(schoolLabel, activeSchool);
        VBox schoolAndStudent = new VBox();
        schoolAndStudent.getChildren().addAll(school, studentName);
        schoolAndStudent.getStyleClass().setAll("box");
        schoolAndStudent.setAlignment(Pos.CENTER);
        VBox viewButtonBox = new VBox();
        viewButtonBox.getChildren().addAll(viewIndButton, viewGroupButton,
                viewArchiveButton);
        viewButtonBox.getStyleClass().setAll("box");
        HBox listLayoutHBox = new HBox();
        listLayoutHBox.getChildren().addAll(visibleStudentList, viewButtonBox);
        listLayoutHBox.getStyleClass().setAll("box");
        VBox listLayoutVBox = new VBox();
        listLayoutVBox.getChildren().addAll(listLabel, listLayoutHBox);
        HBox modifyListButtonBox = new HBox();
        modifyListButtonBox.getChildren().addAll(addStudentButton,
                removeStudentButton, archiveStudentButton);
        modifyListButtonBox.getStyleClass().setAll("box");
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

    private Button initRemoveStudentButton(Properties localProperties,
            StringProperty listStatusTextProperty,
            ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, TextField studentField) {
        Button removeStudentButton = initButton("RemoveStudentButton.png", 110, 66);
        removeStudentButton.setOnAction((ActionEvent event) -> {
            String drop
                    = visibleStudentList.getSelectionModel().getSelectedItem();
            if (drop != null) {
                String[] fileName = new File(localProperties.getProperty("listPath"))
                        .getName().split(" ");

                if (!fileName[0].equals("Modified")) {
                    localProperties.setProperty("listPath", schoolPath
                            + File.separator + "Modified " + new File(
                                    localProperties.getProperty("listPath"))
                            .getName());
                    storeLocalProperties(localProperties);
                    setListStatusTextProperty(localProperties.getProperty("listPath"),
                            listStatusTextProperty);
                }
                removeStudentFromList(studentList, drop,
                        localProperties.getProperty("listPath"));
                setFilteredList(visibleStudentList, observableStudentList,
                        studentField);
            }
        });
        return removeStudentButton;
    }

    private MenuItem buildReadMeMenuItem(Stage primaryStage) {
        //Create menu item for readme.

        MenuItem readMeMenu = new MenuItem("ReadMe");
        readMeMenu.setOnAction((ActionEvent event) -> {
            final Stage readMeStage = new Stage();
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
                System.err.println("Could not read readme file: " + io);
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

    private Button initDirectoryButton(ListView<String> visibleStudentList,
            ObservableList<String> observableStudentList,
            List<String> studentList, TextField studentField, Label listStatusText,
            Properties localProperties, StringProperty listStatusTextProperty,
            Stage primaryStage) {
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
                readStudentList(studentList, localProperties.getProperty("listPath"));
                setFilteredList(visibleStudentList, observableStudentList,
                        studentField);
                setListStatusTextProperty(localProperties.getProperty("listPath"), listStatusTextProperty);
                System.out.println("Setting textfill to white in initDir!");
                listStatusText.setTextFill(rgb(255, 255, 255));
            }
        });
        return dirBtn;
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
