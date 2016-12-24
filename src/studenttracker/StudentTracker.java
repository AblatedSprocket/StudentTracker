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
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import static javafx.application.Application.launch;
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

    String userPath;
    String resourcePath;
    String studentFilesPath;
    static String dbName = "students";
    List<String[]> backgroundList = new ArrayList<>();

    enum Category {
        fullName, firstName, lastName, indStart, indEnd, groupStart, groupEnd,
        checkInStart, checkInEnd, walkIns, forms, notes, hasIEP, has504, hasEval
    }

    @Override

    public void start(Stage primaryStage) {
        //Set class variables excluding selectedStudent.
        userPath = System.getProperty("user.dir");
        resourcePath = userPath + File.separator + "Resources";
        studentFilesPath = resourcePath + File.separator + "Student Files";

        //Check on Databasse
        checkDatabase();

        //Create resources folder if it does not exist.
        new File(resourcePath).mkdir();
        new File(studentFilesPath).mkdir();
        //Obtain properties from properties file or create a properties file if none exists.
        Properties properties = new Properties();
        establishProperties(properties);

        List<String> studentList = new ArrayList<>();
        //Create GUI.
        Image okImage = new Image(getClass().getResourceAsStream(
                "OKButton.png"), 55, 33, true, true);
        Scene scene = new Scene(createLayout(studentList, properties,
                primaryStage));
        scene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        primaryStage.setTitle("Student Tracker");
        primaryStage.setResizable(false);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void checkDatabase() {
        File file = new File(dbName + ".db");
//        if (!file.exists()) {
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn
                        = DriverManager.getConnection("jdbc:sqlite:" + resourcePath + File.separator + dbName + ".db");
                Statement stat = conn.createStatement();
                try {
                    stat.executeUpdate("drop table if exists students;");
                    stat.executeUpdate("drop table if exists archive;");
                    System.out.println("Dropping students table");
                } catch (SQLException sql) {
                    System.err.println("Could not drop table students.");
                }
                try {
                    stat.executeUpdate("create table students (fullName, "
                            + "firstName, lastName, indStart, indEnd, "
                            + "groupStart, groupEnd, checkInStart, checkInEnd, "
                            + "walkIns, forms, notes, hasIEP, has504, hasEval);");
                    stat.executeUpdate("create table archive (fullName, "
                            + "firstName, lastName, indStart, indEnd, "
                            + "groupStart, groupEnd, checkInStart, checkInEnd, "
                            + "walkIns, forms, notes, hasIEP, has504, hasEval);");
                } catch (SQLException sql) {
                    System.err.println("Could not update table.");
                }
            } catch (SQLException sql) {
                System.err.println("Could not connect to database "
                        + dbName);
            }
        } catch (ClassNotFoundException cnf) {
            System.err.println("Could not find java database class");
        }
//        }
    }

    private void modifyDatabase(Category cat, Student student) {
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn = DriverManager.getConnection("jdbc:sqlite:"
                        + dbName + ".db");
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
                            + "students set " + cat.toString() + " = '" + val
                            + "' where fullName = '" + student.getFullName()
                            + "';");
                    update.addBatch();
                    conn.setAutoCommit(false);
                    update.executeBatch();
                    conn.setAutoCommit(true);
                    System.out.println("Database modified, " + cat.toString()
                            + " set to " + val + ".");
                } catch (SQLException sql) {
                    System.err.println("Unable to prepare statement.");
                }

                conn.close();
            } catch (SQLException sql) {
                System.err.println("Could not connect to database for modification.");
            }
        } catch (ClassNotFoundException cnf) {
            System.err.println("Could not find class org.sqlite.JDBC");
        }
    }

    private void refreshDatabase(Student student) {
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn
                        = DriverManager.getConnection("jdbc:sqlite:" + dbName
                                + ".db");
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
                System.out.println("Added all fields to database!");
                prep.addBatch();
                conn.setAutoCommit(false);
                prep.executeBatch();
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException sql) {
                System.err.println("Could not connect to database for access.");

            }
        } catch (ClassNotFoundException cnf) {
            System.err.println("Could not find class org.sqlite.JDBC");
        }
    }

    private void accessDatabase(Student student) {
        try {
            Class.forName("org.sqlite.JDBC");
            try {
                Connection conn
                        = DriverManager.getConnection("jdbc:sqlite:" + dbName
                                + ".db");
                try {
                    Statement stat = conn.createStatement();
                    System.out.println("created statement");
                    ResultSet rs = stat.executeQuery("select * from students where "
                            + "fullName = '" + student.getFullName() + "';");
                    System.out.println("Got result set");
                    System.out.println("Found student, getting data...");
                    student.setStartInd(rs.getString("indStart"));
                    System.out.println(student.getStartInd());
                    student.setEndInd(rs.getString("indEnd"));
                    System.out.println(student.getEndInd());
                    student.setStartGroup(rs.getString("groupStart"));
                    System.out.println(student.getStartGroup());
                    student.setEndGroup(rs.getString("groupEnd"));
                    System.out.println(student.getEndGroup());
                    student.setStartCheckIn(rs.getString("checkInStart"));
                    System.out.println(student.getStartCheckIn());
                    student.setEndCheckIn(rs.getString("checkInEnd"));
                    System.out.println(student.getEndCheckIn());
                    student.setWalkIns(rs.getString("walkIns"));
                    System.out.println(student.getWalkIns());
                    student.setForms(rs.getString("forms"));
                    System.out.println(student.getForms());
                    student.setNotes(rs.getString("notes"));
                    student.setIEP(Boolean.valueOf(rs.getString("hasIEP")));
                    student.set504(Boolean.valueOf(rs.getString("has504")));
                    student.setEval(Boolean.valueOf(rs.getString("hasEval")));
                    rs.close();
                } catch (SQLException sql) {
                    System.out.println("Couldn't find student, adding to "
                            + "database...");
                    PreparedStatement prep = conn.prepareStatement("insert into"
                            + " students values (?, ?, ?, ?, ?, ?, ?, ?, ?, "
                            + "?, ?, ?, ?, ?, ?);");
                    System.out.println("Prepared a statement!");
                    prep.setString(1, student.getFullName());
                    System.out.println("Setting fullName to "
                            + student.getFullName());
                    prep.setString(2, student.getFirstName());
                    System.out.println("Setting firstName to "
                            + student.getFirstName());
                    prep.setString(3, student.getLastName());
                    System.out.println("Setting kastName to "
                            + student.getLastName());
                    prep.setString(4, student.getStartInd());
                    System.out.println("Setting startInd to "
                            + student.getStartInd());
                    prep.setString(5, student.getEndInd());
                    System.out.println("Setting endInd to "
                            + student.getEndInd());
                    prep.setString(6, student.getStartGroup());
                    System.out.println("Setting startGroup to "
                            + student.getStartGroup());
                    prep.setString(7, student.getEndGroup());
                    prep.setString(8, student.getStartCheckIn());
                    prep.setString(9, student.getEndCheckIn());
                    prep.setString(10, student.getWalkIns());
                    System.out.println("Setting walkIns to "
                            + student.getWalkIns());
                    prep.setString(11, student.getForms());
                    prep.setString(12, String.valueOf(student.getIEP()));
                    prep.setString(13, String.valueOf(student.get504()));
                    prep.setString(14, String.valueOf(student.getEval()));
                    prep.addBatch();
                    conn.setAutoCommit(false);
                    prep.executeBatch();
                    conn.setAutoCommit(true);
                }
                conn.close();
            } catch (SQLException sql) {
                System.err.println("Could not connect to database for access.");
            }

        } catch (ClassNotFoundException cnf) {
            System.err.println("Could not find class org.sqlite.JDBC");
        }
    }

    private ListView<String> createVisibleStudentList(List<String> studentList,
            TextField studentField, Stage primaryStage) {
        //Creates visible list for GUI

        //Create internal ObservableList based on studentList.
        ObservableList<String> observableStudentList
                = FXCollections.observableList(studentList);
        //Initialize uneditable list visible in GUI.
        ListView<String> visibleStudentList = new ListView<>();
        visibleStudentList.setEditable(false);
        FilteredList<String> filteredItems = new FilteredList<String>(
                observableStudentList, p -> true);
        //Enable filtering of visibleStudentList based on entry in TextField studentField
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
        //As filteredItems updates, visibleStudentList does also.
        visibleStudentList.setItems(filteredItems);
        //Initiate certificate creation process upon click on list item.
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
                    initStudentStage(student, primaryStage);
                }
            }
        });
        return visibleStudentList;
    }

    private void initStudentStage(Student student, Stage primaryStage) {
        String studentName = student.getFullName();
        Stage studentStage = new Stage();
        studentStage.initOwner(primaryStage);
        studentStage.initModality(Modality.APPLICATION_MODAL);
        studentStage.setTitle("Counseling");
        CheckBox indBox = new CheckBox("Individual");
        indBox.setId("indB");
        setCheck(indBox, student);

        indBox.setOnMouseClicked((MouseEvent a) -> {
            System.out.println("indBox clicked! values for text choice:");
            System.out.println(student.getStartInd());
            System.out.println(student.getEndInd());
            if (student.getStartInd() == null && student.getEndInd() == null) {
                System.out.println("Going with setting start date");
                addBoxFunction(indBox, student, Category.indStart);
            } else if (student.getEndInd() == null) {
                System.out.println("Going with setting end date");
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

        Button detailsButton = createDetailsButton(student, studentStage);

        VBox studentBox = new VBox();
        Label title = new Label("Counseling enrollment for " + studentName);
        HBox buttons = new HBox();
        buttons.setId("buttonBox");
        buttons.getChildren().addAll(addWalkInButton, detailsButton);
        studentBox.getChildren().addAll(title, indBox, groupBox, checkInBox,
                modDate);
        VBox contentBox = new VBox();
        contentBox.setId("buttonBox");
        contentBox.getChildren().addAll(studentBox, buttons);
        Scene studentScene = new Scene(contentBox);
        studentScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        studentStage.setScene(studentScene);
        studentStage.show();
    }

    private Button createDetailsButton(Student student, Stage studentStage) {
        Button detailsButton = new Button();
        Image detailsImage = new Image(getClass().getResourceAsStream(
                "DetailsButton.png"), 110, 66, true, true);
        detailsButton.setGraphic(new ImageView(detailsImage));
        detailsButton.setOnAction((ActionEvent event) -> {
            initDetailsStage(student, studentStage);
        });
        return detailsButton;
    }

    private void initDetailsStage(Student student, Stage studentStage) {
        Stage detailsStage = new Stage();
        detailsStage.setTitle("Student Details");
        detailsStage.initOwner(studentStage);
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
            ffBox.setSelected(student.getIEP());
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

        HBox formButtons = new HBox();
        formButtons.setId("buttonBox");
        formButtons.getChildren().addAll(addFormButton, removeFormButton);
        Label noteText = new Label("Notes:");
        TextArea noteArea = new TextArea();
        noteArea.setText(student.getNotes());
        noteArea.setMaxSize(280, 500);
        noteArea.setWrapText(true);
        Button okButton = new Button();
        Image okImage = new Image(getClass().getResourceAsStream(
                "OKButton.png"), 55, 33, true, true);
        okButton.setGraphic(new ImageView(okImage));
        okButton.setId("okButton");
        okButton.setOnAction((ActionEvent event) -> {
            student.setNotes(noteArea.getText());
            modifyDatabase(Category.notes, student);
            detailsStage.close();
        });
        VBox noteLayout = new VBox();
        noteLayout.getChildren().addAll(noteText, noteArea);
        VBox formLayout = new VBox();
        formLayout.getChildren().addAll(formText, visibleFormList);
        VBox checkBoxLayout = new VBox();
        checkBoxLayout.getChildren().addAll(IEPBox, ffBox, evalBox);
        VBox stageLayout = new VBox();
        stageLayout.setId("buttonBox");
        stageLayout.getChildren().addAll(checkBoxLayout, formLayout, 
                formButtons, noteLayout, okButton);
        Scene detailsScene = new Scene(stageLayout, 300, 500);
        detailsScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        detailsStage.setScene(detailsScene);
        detailsStage.show();
    }

    private Button initAddFormButton(Student student, Stage parentStage,
            List forms, ListView visibleList) {
        Button addFormButton = new Button();
        Image addFormImage = new Image(getClass().getResourceAsStream(
                "AddFormButton.png"), 110, 66, true, true);
        addFormButton.setGraphic(new ImageView(addFormImage));
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
        Button removeFormButton = new Button();
        Image removeFormImage = new Image(getClass().getResourceAsStream(
                "RemoveFormButton.png"), 110, 66, true, true);
        removeFormButton.setGraphic(new ImageView(removeFormImage));
        removeFormButton.setOnAction((ActionEvent event) -> {
            if (!forms.isEmpty()) {
                String fileName = (String) visibleList.getSelectionModel()
                        .getSelectedItem();
                if (fileName != null) {
                    File removeFile = new File(studentFilesPath + File.separator
                            + student.getFullName() + File.separator + fileName);
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
        Button addWalkInButton = new Button();
        Image walkInImage = new Image(getClass().getResourceAsStream(
                "AddWalkInButton.png"), 110, 66, true, true);
        addWalkInButton.setGraphic(new ImageView(walkInImage));
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
        System.out.println("Checking if box should be checked!");
        switch (box.getId()) {
            case "indB":
                System.out.println("indEnd = " + student.getEndInd());
                System.out.println("indStart = " + student.getStartInd());
                if (student.getEndInd() != null) {
                    box.setIndeterminate(true);
                    System.out.println("Setting box to indeterminate");
                } else if (student.getStartInd() != null) {
                    System.out.println("startInd = " + student.getStartInd());
                    box.setIndeterminate(false);
                    box.setSelected(true);
                    System.out.println("Setting box to checked");
                } else {
                    box.setIndeterminate(false);
                    box.setSelected(false);
                    System.out.println("Setting box to unchecked");
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

        //Declare nodes for dialog box
        Label warn = new Label("Replace any incorrect dates and press the"
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
        System.out.println("student indStart date is " + student.getStartInd());
        System.out.println("Setting startIndField's text to "
                + startIndField.getText());
        endIndField.setText(student.getEndInd());
        startGroupField.setText(student.getStartGroup());
        endGroupField.setText(student.getEndGroup());
        startCheckInField.setText(student.getStartCheckIn());
        endCheckInField.setText(student.getEndCheckIn());
        Stage modStudentStage = new Stage();

        Button okButton = new Button();
        Image okImage = new Image(getClass().getResourceAsStream(
                "OKButton.png"), 55, 33, true, true);
        okButton.setGraphic(new ImageView(okImage));
        okButton.setId("okButton");
        okButton.setOnAction((ActionEvent event) -> {
            try {
                System.out.println("startIndField's text \""
                        + startIndField.getText() + "\"");
                checkFieldText(startIndField, student);
                System.out.println("Setting startInd to "
                        + student.getStartInd());
                System.out.println("endIndField's text \""
                        + endIndField.getText() + "\"");
                checkFieldText(endIndField, student);
                System.out.println("Setting endInd to "
                        + student.getEndInd());
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
                improperFormatAlert();
            }

            setCheck(indBox, student);
            setCheck(groupBox, student);
            setCheck(checkInBox, student);
        });

        //Lay out the stage
        Button cancelButton = new Button();
        Image cancelImage = new Image(getClass().getResourceAsStream(
                "CancelButton.png"), 55, 33, true, true);
        cancelButton.setGraphic(new ImageView(cancelImage));
        cancelButton.setOnAction((ActionEvent event) -> {
            modStudentStage.close();
        });
        HBox buttonBox = new HBox();
        buttonBox.setId("buttonBox");
        buttonBox.getChildren().addAll(okButton, cancelButton);
        modStudentStage.initModality(Modality.APPLICATION_MODAL);
        VBox stageLayout = new VBox();
        GridPane datePane = new GridPane();
        datePane.setId("gridPane");
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
        datePane.add(buttonBox, 1, 7);
        stageLayout.getChildren().addAll(warn, datePane);
        Scene scene = new Scene(stageLayout);
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
                improperFormatAlert();
                System.out.println("Unselecting box");
            }
        }
        System.out.println("Category passed to checkStudent is "
                + cat.toString());
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
                System.out.println("indStart is being modified to " + date);
                student.setStartInd(date);
                break;
            case "indEnd":
                System.out.println("indEnd is being modified to " + date);
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

    private void improperFormatAlert() {
        Alert impFormatAlert = new Alert(Alert.AlertType.WARNING,
                "Specified date was not formatted properly."
                + " Please use \"mm-dd-yyyy\" format.");
        DialogPane dialogPane = impFormatAlert.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("Main.css")
                .toExternalForm());
        dialogPane.getStyleClass().add("myDialog");
        impFormatAlert.showAndWait();
    }

    private Label createStatusText(StringProperty inputTxt) {
        //Creates and binds TextProperties for GUI status messages.
        Label statusText = new Label();
        statusText.setId("statusText");
        statusText.textProperty().bind(inputTxt);
        return statusText;
    }

    private void initAddStudentStage(String stdntLstPath,
            Properties properties, Stage primaryStage) {
        Stage addStudentStage = new Stage();
        addStudentStage.setTitle("Add Student");
        addStudentStage.initOwner(primaryStage);
        addStudentStage.initModality(Modality.APPLICATION_MODAL);
        Label addText = new Label("Type the name of the student you would like "
                + "to add and press OK");
        TextField addField = new TextField();
        Button okButton = new Button();
        Image okImage = new Image(getClass().getResourceAsStream(
                "OKButton.png"), 55, 33, true, true);
        okButton.setGraphic(new ImageView(okImage));
        okButton.setId("okButton");
        Button cancelButton = new Button();
        Image cancelImage = new Image(getClass().getResourceAsStream(
                "CancelButton.png"), 55, 33, true, true);
        cancelButton.setGraphic(new ImageView(cancelImage));
        cancelButton.setId("cancelButton");
        cancelButton.setOnAction((ActionEvent event) -> {
            addStudentStage.close();
        });
        okButton.setOnAction((ActionEvent event) -> {
            System.out.println("Taking action ?" + (addField.getText() != null
                    && !addField.getText().equals("")));
            if (addField.getText() != null && !addField.getText().equals("")) {
                System.out.println("We're taking action!");
                String[] name = addField.getText().split(" ");
                if (name.length == 3) {
                    name[1] += " " + name[2];
                }
                String[] formattedName = {name[1], name[0]};
                String[] fileName = new File(stdntLstPath).getName().split(" ");
                System.out.println("We split the filename to find out if a "
                        + "modified list exists.");
                System.out.println("File name is " + fileName);
                if (fileName[0].equals("Modified")) {
                    System.out.println("Modified key word recognized.");
                    modifyStudentList(formattedName, stdntLstPath, "add");
                    System.out.println("Trying to reset visible list.");
                    resetVisibleList(properties, stdntLstPath);
                } else {
                    System.out.println("Modified variable not found, adding.");
                    String modListPath = resourcePath + File.separator
                            + "Modified " + new File(stdntLstPath).getName();
                    modifyStudentList(formattedName, modListPath, "add");
                    System.out.println("Trying to reset visible list.");
                    resetVisibleList(properties, modListPath);
                }
            }
        });
        HBox buttons = new HBox();
        buttons.setId("buttonBox");
        buttons.getChildren().addAll(okButton, cancelButton);
        VBox addBox = new VBox();
        addBox.getChildren().addAll(addText, addField);
        VBox layout = new VBox();
        layout.setId("buttonBox");
        layout.getChildren().addAll(addBox, buttons);
        Scene addScene = new Scene(layout);
        addScene.getStylesheets().add(StudentTracker.class.getResource(
                "Main.css").toExternalForm());
        addStudentStage.setScene(addScene);
        addStudentStage.show();
    }

    private void modifyStudentList(String[] name, String listPath, String op) {

        List<String> writeList = new ArrayList<>();
        if (op.equals("add")) {
            backgroundList.add(name);
        }
        for (int i = 0; i < backgroundList.size(); i++) {
            writeList.add(backgroundList.get(i)[0] + " \t"
                    + backgroundList.get(i)[1]);
        }
        if (op.equals("rem")) {

            String remName = name[0].trim() + " \t" + name[1].trim();
            Boolean check = writeList.remove(remName);
            System.out.println("we're removing a name!" + check);
        } else {
            Collections.sort(writeList);
        }

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

    private void extractStudentList(List<String> studentList,
            String stdntLstPath) {
        //extracts student list from text file.

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
            int lines = 0;
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
            System.err.println("I/O Exception in method extractStudentList: "
                    + io);
            Alert studentListNotFound = new Alert(Alert.AlertType.WARNING,
                    "Specified student list was not found. "
                    + "Please choose a different list.");
            studentListNotFound.showAndWait();
        } catch (NullPointerException npe) {
            Alert templateNotFound = new Alert(Alert.AlertType.WARNING,
                    "Specified student list was not found. "
                    + "Please choose a different list.");
            templateNotFound.showAndWait();
        }
    }

    private void establishProperties(Properties properties) {
        //Retrieves properties or creates properties file if none exists.

        //define properties file path.
        Path propPath = Paths.get(resourcePath + File.separator
                + "data.properties");
        //If file does not exist, create it.
        if (Files.notExists(propPath)) {
            try {
                Files.createFile(propPath);
            } catch (IOException io) {
                System.err.println("Could not create properties file: " + io);
            }
            //Set property values to pass to created file.
            String studentListPath = "";
            properties.setProperty("studentListPath", studentListPath);
            //Write properties to created properties file.
            try (FileOutputStream writeProp = new FileOutputStream(
                    resourcePath + File.separator + "data.properties")) {
                properties.store(writeProp, null);
            } catch (IOException io) {
                System.err.println("Could not write properties to file: " + io);
            }
        } else {
            //Load properties from existing properties file.
            try {
                properties.load(new FileInputStream(propPath.toString()));
            } catch (IOException io) {
                System.err.println("Could not load properties: " + io);
            }
        }
    }

    private void copy(File source, File target)
            throws IllegalArgumentException {
        /* copy 
        
         */
        System.out.println("Source file: " + source.getName());
        System.out.println("Target file: " + target.getName());
        System.out.println("Source and target files are the same? "
                + source.getName().equals(target.getName()));
        if (!source.getName().equals(target.getName())) {
            try {
                if (target.exists()) {
                    Alert fileExistsAlert = new Alert(
                            Alert.AlertType.CONFIRMATION,
                            "File already exists. Overwrite?");
                    fileExistsAlert.showAndWait().ifPresent(response -> {

                        if (response != ButtonType.OK) {
                            throw new IllegalArgumentException();
                        }
                    });
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

    private VBox createLayout(List<String> studentList, Properties properties,
            Stage primaryStage) {
        //Creates GUI elements and layout.

        //Create elements for studentList status text.
        String stdntLstPath = properties.getProperty("studentListPath");
        StringProperty stdntLstTxt = new SimpleStringProperty();
        Label listStatusText = createStatusText(stdntLstTxt);
        if (stdntLstPath.isEmpty()) {
            stdntLstTxt.set("No student list has been selected");
            listStatusText.setTextFill(rgb(255, 0, 0));
        } else {
            stdntLstTxt.set("Selected Student List:\n" + stdntLstPath);
            extractStudentList(studentList, stdntLstPath);
        }

        //Create elements for left half of GUI.
        Label stdntLbl = new Label("Student Name:");
        TextField stdntFld = new TextField();
        ListView<String> visibleStudentList = createVisibleStudentList(
                studentList, stdntFld, primaryStage);
        Button setDirBtn = createDirectoryButton(properties, primaryStage);
        //Place elements in multiple VBoxes for positioning purposes.
        VBox studentName = new VBox();
        studentName.getChildren().add(stdntLbl);
        studentName.getChildren().add(stdntFld);
        VBox directoryInfo = new VBox();
        directoryInfo.setId("studentInfo");
        directoryInfo.getChildren().add(studentName);
        directoryInfo.getChildren().add(setDirBtn);
        directoryInfo.getChildren().add(listStatusText);
        listStatusText.setTextOverrun(LEADING_ELLIPSIS);
        VBox userInput = new VBox();
        userInput.setId("userInput");
        userInput.getChildren().add(directoryInfo);
        VBox listBox = new VBox();
        Label studentListText = new Label("Student List:");
        listBox.getChildren().addAll(studentListText, visibleStudentList);
        //Create elements for right half of GUI.

        Button addStudentButton = new Button();
        Image addStudentImage = new Image(getClass().getResourceAsStream(
                "AddStudentButton.png"), 110, 66, true, true);
        addStudentButton.setGraphic(new ImageView(addStudentImage));
        addStudentButton.setOnAction((ActionEvent event) -> {
            initAddStudentStage(stdntLstPath, properties, primaryStage);
        });
        Button removeStudentButton = initRemoveStudentButton(properties,
                visibleStudentList, stdntLstPath);
        Image removeStudentImage = new Image(getClass().getResourceAsStream(
                "RemoveStudentButton.png"), 110, 66, true, true);
        removeStudentButton.setGraphic(new ImageView(removeStudentImage));
        HBox buttons = new HBox();
        buttons.setId("buttonBox");
        buttons.setId("listButtons");
        buttons.getChildren().addAll(addStudentButton, removeStudentButton);
        VBox rightSide = new VBox();
        rightSide.setId("buttonBox");
        rightSide.getChildren().addAll(listBox, buttons);
        //Combine left and right halves of GUI in HBox.
        HBox content = new HBox();
        content.setId("content");
        content.getChildren().addAll(userInput, rightSide);
        //Create MenuBar for access to readme file.
        MenuBar menuBar = new MenuBar();
        //Create File menu item.
        Menu menuFile = new Menu("File");
        //Create readme menu item under file.
        MenuItem readMeMenu = createReadMeMenuItem(primaryStage);
        menuFile.getItems().add(readMeMenu);
        //Create close menu item under readme
        MenuItem closeMenu = createCloseMenuItem();
        menuFile.getItems().add(closeMenu);
        //Add File menu to menuBar.
        menuBar.getMenus().add(menuFile);
        VBox layout = new VBox();
        layout.getChildren().add(menuBar);
        layout.getChildren().add(content);

        return layout;
    }

    private Button initRemoveStudentButton(Properties properties,
            ListView<String> visibleStudentList, String stdntLstPath) {
        Button removeStudentButton = new Button();
        removeStudentButton.setOnAction((ActionEvent event) -> {
            String drop
                    = visibleStudentList.getSelectionModel().getSelectedItem();
            if (drop != null) {
                String[] name = drop.split(" ");
                if (name.length == 3) {
                    name[1] += " " + name[2];
                }
                String[] formattedName = {name[1], name[0]};
                String[] fileName = new File(stdntLstPath).getName().split(" ");

                if (fileName[0].equals("Modified")) {
                    modifyStudentList(formattedName, stdntLstPath, "rem");
                    resetVisibleList(properties, stdntLstPath);
                } else {
                    String modListPath = resourcePath + File.separator
                            + "Modified " + new File(stdntLstPath).getName();
                    modifyStudentList(formattedName, modListPath, "rem");
                    resetVisibleList(properties, modListPath);
                }
            }
        });
        return removeStudentButton;
    }

    private MenuItem createReadMeMenuItem(Stage primaryStage) {
        //Create menu item for readme.

        MenuItem readMeMenu = new MenuItem("ReadMe");
        //Make readme window open when menu item is clicked.
        readMeMenu.setOnAction((ActionEvent event) -> {
            final Stage readMeStage = new Stage();
            readMeStage.initModality(Modality.NONE);
            readMeStage.initOwner(primaryStage);
            readMeStage.setTitle("ReadMe");
            VBox dialogVbox = new VBox(20);
            //Read in text from readme source file.
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    getClass().getResourceAsStream("readme.txt")));
            StringBuilder readMeLines = new StringBuilder();
            try {
                getText(readMeLines, br);
            } catch (IOException io) {
                System.err.println("Could not read readme file: " + io);
            }
            //Add text to readme window
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

    private MenuItem createCloseMenuItem() {
        //Create menu item to exit application.

        MenuItem closeMenu = new MenuItem("Close");
        closeMenu.setOnAction((ActionEvent event) -> {
            System.exit(0);
        });
        return closeMenu;
    }

    private Button createDirectoryButton(Properties properties,
            Stage primaryStage) {
        //Create button to choose student list directory.

        //Create button.
        Button dirBtn = new Button();
        Image imageDir = new Image(getClass().getResourceAsStream("SetListButton.png"), 110, 66, true, true);
        dirBtn.setGraphic(new ImageView(imageDir));
        dirBtn.setId("dirBtn");
        //Prompt user to choose student list file.
        FileChooser chooseList = new FileChooser();
        chooseList.setInitialDirectory(new File(resourcePath));
        chooseList.setTitle("Select Student List File");
        dirBtn.setOnAction((final ActionEvent dirClick) -> {
            File listFile = chooseList.showOpenDialog(primaryStage);
            //Perform actions only if a new list is chosen.
            if (listFile != null) {
                //Modify properties file to reflect change in path of student list file.
                File target = new File(resourcePath + File.separator
                        + listFile.getName());
                copy(listFile, target);
                resetVisibleList(properties, resourcePath + File.separator
                        + listFile.getName());

            }
        });
        return dirBtn;
    }

    private void resetVisibleList(Properties properties, String listFile) {
        properties.setProperty("studentListPath", listFile);
        try {
            FileOutputStream saveProp = new FileOutputStream(resourcePath
                    + File.separator + "data.properties");
            try {
                properties.store(saveProp, null);
            } catch (IOException io) {
                System.err.println("Could not update properties: " + io);
            }
            restartApplication();
        } catch (FileNotFoundException fnf) {
            System.err.println("Could not find save to properties file: "
                    + fnf);
        }
    }

    private void getText(StringBuilder readMeLines, BufferedReader br)
            throws IOException {
        //Retrieves content from readme file.

        int i = 0;
        String line = "";

        while (line != null) {
            readMeLines.append(line).append("\n");
            line = br.readLine();
        }
    }

    private void restartApplication() {
        //Restarts application

        final String javaBin = System.getProperty("java.home")
                + File.separator + "bin" + File.separator + "java";
        try {
            final File currentJar = new File(StudentTracker.class
                    .getProtectionDomain().getCodeSource().getLocation()
                    .toURI());
            if (!currentJar.getName().endsWith(".jar")) {
                return;
            }
            final ArrayList<String> command = new ArrayList<>();
            command.add(javaBin);
            command.add("-jar");
            command.add(currentJar.getPath());
            final ProcessBuilder builder = new ProcessBuilder(command);
            try {
                builder.start();
            } catch (IOException io) {
                System.err.println("I/O exception, program could not be "
                        + "restarted." + io);
            }
            System.exit(0);
        } catch (URISyntaxException uri) {
            System.err.println("URI syntax exception: " + uri);
        }
    }
}
