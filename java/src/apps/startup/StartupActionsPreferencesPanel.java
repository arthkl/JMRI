package apps.startup;

import apps.ConfigBundle;
import apps.StartupActionsManager;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.IndexedPropertyChangeEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import jmri.InstanceManager;
import jmri.profile.ProfileManager;
import jmri.swing.PreferencesPanel;

/**
 * Preferences panel to configure optional actions taken at startup.
 *
 * @author Randall Wood (C) 2016
 */
public class StartupActionsPreferencesPanel extends JPanel implements PreferencesPanel {

    /**
     * Creates new form StartupActionsPreferencesPanel
     */
    public StartupActionsPreferencesPanel() {
        initComponents();
        this.actionsTbl.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            int row = this.actionsTbl.getSelectedRow();
            this.upBtn.setEnabled(row != 0 && row != -1);
            this.downBtn.setEnabled(row != this.actionsTbl.getRowCount() - 1 && row != -1);
            this.removeBtn.setEnabled(row != -1);
        });
        ArrayList<JMenuItem> items = new ArrayList<>();
        InstanceManager.getDefault(StartupActionsManager.class).getFactories().values().stream().forEach((factory) -> {
            JMenuItem item = new JMenuItem(factory.getActionText());
            item.addActionListener((ActionEvent e) -> {
                StartupModel model = factory.newModel();
                factory.editModel(model, this.getTopLevelAncestor());
                if (model.getName() != null && !model.getName().isEmpty()) {
                    InstanceManager.getDefault(StartupActionsManager.class).addAction(model);
                }
            });
            items.add(item);
        });
        items.sort((JMenuItem o1, JMenuItem o2) -> o1.getText().compareTo(o2.getText()));
        items.stream().forEach((item) -> {
            this.actionsMenu.add(item);
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        actionsMenu = new JPopupMenu();
        jScrollPane1 = new JScrollPane();
        actionsTbl = new JTable() {
            //Implement table cell tool tips.
            public String getToolTipText(MouseEvent e) {
                try {
                    return getValueAt(rowAtPoint(e.getPoint()), -1).toString();
                } catch (RuntimeException e1) {
                    //catch null pointer exception if mouse is over an empty line
                }
                return null;
            }
        };
        addBtn = new JButton();
        removeBtn = new JButton();
        startupLbl = new JLabel();
        upBtn = new JButton();
        downBtn = new JButton();
        moveLbl = new JLabel();
        recommendationsLbl = new JLabel();

        actionsTbl.setDefaultRenderer(StartupModel.class, new StartupModelCellRenderer());
        actionsTbl.setDefaultEditor(StartupModel.class, new StartupModelCellEditor());
        actionsTbl.setModel(new TableModel(InstanceManager.getDefault(StartupActionsManager.class)));
        actionsTbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actionsTbl.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(actionsTbl);
        actionsTbl.getColumnModel().getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        ResourceBundle bundle = ResourceBundle.getBundle("apps/startup/Bundle"); // NOI18N
        addBtn.setText(bundle.getString("StartupActionsPreferencesPanel.addBtn.text")); // NOI18N
        addBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                addBtnActionPerformed(evt);
            }
        });

        removeBtn.setText(bundle.getString("StartupActionsPreferencesPanel.removeBtn.text")); // NOI18N
        removeBtn.setEnabled(false);
        removeBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                removeBtnActionPerformed(evt);
            }
        });

        startupLbl.setText(bundle.getString("StartupActionsPreferencesPanel.startupLbl.text")); // NOI18N

        upBtn.setText(bundle.getString("StartupActionsPreferencesPanel.upBtn.text")); // NOI18N
        upBtn.setEnabled(false);
        upBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                upBtnActionPerformed(evt);
            }
        });

        downBtn.setText(bundle.getString("StartupActionsPreferencesPanel.downBtn.text")); // NOI18N
        downBtn.setEnabled(false);
        downBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                downBtnActionPerformed(evt);
            }
        });

        moveLbl.setText(bundle.getString("StartupActionsPreferencesPanel.moveLbl.text")); // NOI18N

        recommendationsLbl.setText(bundle.getString("StartupActionsPreferencesPanel.recommendationsLbl.text")); // NOI18N

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(recommendationsLbl)
                    .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 487, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addBtn)
                        .addGap(18, 18, 18)
                        .addComponent(moveLbl)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upBtn)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(downBtn)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(removeBtn))
                    .addComponent(startupLbl))
                .addContainerGap())
        );
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(startupLbl, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(recommendationsLbl, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(addBtn)
                    .addComponent(removeBtn)
                    .addComponent(upBtn)
                    .addComponent(downBtn)
                    .addComponent(moveLbl))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addBtnActionPerformed(ActionEvent evt) {//GEN-FIRST:event_addBtnActionPerformed
        Component c = (Component) evt.getSource();
        this.actionsMenu.show(c, 0 - 1, c.getHeight());
    }//GEN-LAST:event_addBtnActionPerformed

    private void removeBtnActionPerformed(ActionEvent evt) {//GEN-FIRST:event_removeBtnActionPerformed
        int row = this.actionsTbl.getSelectedRow();
        if (row != -1) {
            StartupModel model = InstanceManager.getDefault(StartupActionsManager.class).getActions(row);
            InstanceManager.getDefault(StartupActionsManager.class).removeAction(model);
        }
    }//GEN-LAST:event_removeBtnActionPerformed

    private void upBtnActionPerformed(ActionEvent evt) {//GEN-FIRST:event_upBtnActionPerformed
        int row = this.actionsTbl.getSelectedRow();
        if (row != 0) {
            InstanceManager.getDefault(StartupActionsManager.class).moveAction(row, row - 1);
            this.actionsTbl.setRowSelectionInterval(row - 1, row - 1);
        }
    }//GEN-LAST:event_upBtnActionPerformed

    private void downBtnActionPerformed(ActionEvent evt) {//GEN-FIRST:event_downBtnActionPerformed
        int row = this.actionsTbl.getSelectedRow();
        if (row != this.actionsTbl.getRowCount() - 1) {
            InstanceManager.getDefault(StartupActionsManager.class).moveAction(row, row + 1);
            this.actionsTbl.setRowSelectionInterval(row + 1, row + 1);
        }
    }//GEN-LAST:event_downBtnActionPerformed

    @Override
    public String getPreferencesItem() {
        return "STARTUP"; // NOI18N
    }

    @Override
    public String getPreferencesItemText() {
        return ConfigBundle.getMessage("MenuStartUp"); // NOI18N
    }

    @Override
    public String getTabbedPreferencesTitle() {
        return null;
    }

    @Override
    public String getLabelKey() {
        return null;
    }

    @Override
    public JComponent getPreferencesComponent() {
        return this;
    }

    @Override
    public boolean isPersistant() {
        return true;
    }

    @Override
    public String getPreferencesTooltip() {
        return null;
    }

    @Override
    public void savePreferences() {
        InstanceManager.getDefault(StartupActionsManager.class).savePreferences(ProfileManager.getDefault().getActiveProfile());
    }

    @Override
    public boolean isDirty() {
        return InstanceManager.getDefault(StartupActionsManager.class).isDirty();
    }

    @Override
    public boolean isRestartRequired() {
        return InstanceManager.getDefault(StartupActionsManager.class).isRestartRequired();
    }

    @Override
    public boolean isPreferencesValid() {
        // To really test would require that the models know their valid state
        // they don't, and it can change externally, so we don't really check.
        return true;
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    JPopupMenu actionsMenu;
    JTable actionsTbl;
    JButton addBtn;
    JButton downBtn;
    JScrollPane jScrollPane1;
    JLabel moveLbl;
    JLabel recommendationsLbl;
    JButton removeBtn;
    JLabel startupLbl;
    JButton upBtn;
    // End of variables declaration//GEN-END:variables

    private class TableModel extends AbstractTableModel implements PropertyChangeListener {

        private final StartupActionsManager manager;

        @SuppressWarnings("LeakingThisInConstructor")
        public TableModel(StartupActionsManager manager) {
            this.manager = manager;
            this.manager.addPropertyChangeListener(this);
        }

        @Override
        public int getRowCount() {
            return this.manager.getActions().length;
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            StartupModel model = this.manager.getActions(rowIndex);
            switch (columnIndex) {
                case -1: // tooltip
                    return model.toString();
                case 0:
                    return model;
                case 1:
                    return this.manager.getFactories(model.getClass()).getDescription();
                default:
                    return null;
            }

        }

        @Override
        public String getColumnName(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return Bundle.getMessage("StartupActionsTableModel.name"); // NOI18N
                case 1:
                    return Bundle.getMessage("StartupActionsTableModel.type"); // NOI18N
                default:
                    return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return StartupModel.class;
                case 1:
                    return String.class;
                default:
                    return null;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            int index = -1;
            if (evt instanceof IndexedPropertyChangeEvent) {
                index = ((IndexedPropertyChangeEvent) evt).getIndex();
            }
            if (index != -1 && evt.getOldValue() instanceof Integer) {
                this.fireTableRowsUpdated((Integer) evt.getOldValue(), index);
            } else {
                this.fireTableDataChanged();
            }
            this.manager.setRestartRequired();
        }
    }
}
