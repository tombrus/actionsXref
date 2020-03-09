package com.tombrus.xref;

import static java.awt.event.KeyEvent.*;
import static javax.swing.JComponent.*;
import static javax.swing.KeyStroke.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Paths;

import com.tombrus.persistentqueues.HumanReadable;
import com.tombrus.persistentqueues.TimerThread;

public class MainForm extends JDialog {
    private final Xref      xref;
    private       JPanel    contentPane;
    //
    private       JLabel    total;
    private       JLabel    absent;
    private       JLabel    repos;
    private       JLabel    noWorkflow;
    private       JLabel    withWorkflow;
    private       JCheckBox activeCheckBox;
    private       JLabel    infoLabel;
    private       JLabel    gotWfsQueue;
    private       JPanel    indicatorA;
    private       JPanel    indicatorB;
    private       JPanel    indicatorC;
    private       JLabel    errorsQueue;
    private       JButton   saveButton;

    public MainForm() {
        setContentPane(contentPane);
        setModal(true);
        setMinimumSize(new Dimension(480, 300));

        // call onQuit() when cross is clicked or on Escape
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onSave(true);
            }
        });
        contentPane.registerKeyboardAction(e -> onSave(true), getKeyStroke(VK_ESCAPE, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        activeCheckBox.addActionListener(e -> onActiveChanged());
        saveButton.addActionListener(e -> onSave(false));

        xref = new Xref(Paths.get("knowhow"), null, 60);
        activeCheckBox.setSelected(!xref.isPaused());

        new TimerThread("refresher", 200, () -> SwingUtilities.invokeLater(this::refresh));

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void refresh() {
        if (xref.isWaitingForPaused()) {
            activeCheckBox.setEnabled(false);
            activeCheckBox.setSelected(false);
        } else if (xref.isWaitingForUnpaused()) {
            activeCheckBox.setEnabled(false);
            activeCheckBox.setSelected(true);
        } else if (xref.isPaused()) {
            activeCheckBox.setEnabled(true);
            activeCheckBox.setSelected(false);
        } else {
            activeCheckBox.setEnabled(true);
            activeCheckBox.setSelected(true);
        }
        total.setText("" + xref.size());
        absent.setText("" + xref.getAbsentQueue().size());
        repos.setText("" + xref.getTmpNamedQueue().size());
        noWorkflow.setText("" + xref.getNowfQueue().size());
        withWorkflow.setText("" + xref.getWfQueue().size());
        gotWfsQueue.setText("" + xref.getGotWfsQueue().size());
        errorsQueue.setText("" + xref.getErrorsQueue().size());

        indicatorA.setBackground(xref.isActorAPaused() ? Color.red : xref.isRateLimited() ? Color.orange : Color.green);
        indicatorB.setBackground(xref.isActorBPaused() ? Color.red : Color.green);
        indicatorC.setBackground(xref.isActorCPaused() ? Color.red : Color.green);

        long max   = Runtime.getRuntime().maxMemory();
        long total = Runtime.getRuntime().totalMemory();
        long free  = Runtime.getRuntime().freeMemory();
        infoLabel.setText("restore " + xref.getLastRestoreMs() + " ms, save " + xref.getLastSaveMs() + " ms, " + HumanReadable.format(xref.getDiskSpace(), 2) + " on disk (" + (max - (total - free)) / (1024 * 1024) + " Mb free)");
    }

    private void onActiveChanged() {
        activeCheckBox.setEnabled(false);
        if (activeCheckBox.isSelected()) {
            xref.unpause(false);
        } else {
            xref.pause(false);
        }
    }

    private void onSave(boolean andQuit) {
        saveButton.setEnabled(false);
        xref.pause(false);
        SwingUtilities.invokeLater(() -> saveWhenPaused(andQuit));
    }

    private void saveWhenPaused(boolean andQuit) {
        if (xref.isPaused()) {
            System.err.println("++++ paused => save " + (andQuit ? " andQuit" : ""));
            xref.carefullSave(!andQuit);
            saveButton.setEnabled(true);
            System.err.println("++++ save done");
            if (andQuit) {
                System.err.println("++++ DISPOSE");
                dispose();
            }
        } else {
            System.err.println("++++ not paused....");
            TimerThread.sleep_(100);
            SwingUtilities.invokeLater(() -> saveWhenPaused(andQuit));
        }
    }

    public static void main(String[] args) {
        new MainForm();
        System.exit(0);
    }
}
