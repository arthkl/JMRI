//ComboRadioButtons.java

package jmri.jmrit.symbolicprog;

import java.util.*;
import javax.swing.*;

import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;

/* Represents a JComboBox as a JPanel of radio buttons.
 *
 * @author			Bob Jacobsen   Copyright (C) 2001
 * @version			$Revision: 1.8 $
 */
public class ComboRadioButtons extends JPanel {

    ButtonGroup g = new ButtonGroup();

    ComboRadioButtons(JComboBox box, EnumVariableValue var) {
        super();
        _var = var;
        _value = var._value;
        _box = box;
        l1 = new ActionListener[box.getItemCount()];
        b1 = new JRadioButton[box.getItemCount()];

        // create the buttons, include in group, listen for changes by name
        for (int i=0; i<box.getItemCount(); i++) {
            String name = ((String)(box.getItemAt(i)));
            JRadioButton b = new JRadioButton( name );
            b1[i] = b;
            b.setActionCommand(name);
            b.addActionListener(l1[i] = new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    thisActionPerformed(e);
                }
            });
            v.addElement(b);
            addToPanel(b, i);
            g.add(b);
        }
        setColor();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        // listen for changes to original
        _box.addActionListener(l2 = new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                originalActionPerformed(e);
            }
        });
        // listen for changes to original state
        _var.addPropertyChangeListener(p1 = new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent e) {
                originalPropertyChanged(e);
            }
        });

        // set initial value
        v.elementAt(_box.getSelectedIndex()).setSelected(true);
    }

    ComboRadioButtons(JComboBox box, IndexedEnumVariableValue var) {
        super();
        _var = var;
        _value = var._value;
        _box = box;
        l1 = new ActionListener[box.getItemCount()];
        b1 = new JRadioButton[box.getItemCount()];

        // create the buttons, include in group, listen for changes by name
        for (int i=0; i<box.getItemCount(); i++) {
            String name = ((String)(box.getItemAt(i)));
            JRadioButton b = new JRadioButton( name );
            b1[i] = b;
            b.setActionCommand(name);
            b.addActionListener(l1[i] = new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    thisActionPerformed(e);
                }
            });
            v.addElement(b);
            addToPanel(b, i);
            g.add(b);
        }
        setColor();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        // listen for changes to original
        _box.addActionListener(l2 = new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                originalActionPerformed(e);
            }
        });
        // listen for changes to original state
        _var.addPropertyChangeListener(p1 = new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent e) {
                originalPropertyChanged(e);
            }
        });

        // set initial value
        v.elementAt(_box.getSelectedIndex()).setSelected(true);
    }

    /**
     * Add a button to the panel if desired.  In this class,
     * its always added, but in the On and Off subclasses, its only
     * added for certain ones
     */
    void addToPanel(JRadioButton b, int i) {
        add(b);
    }

    void thisActionPerformed(java.awt.event.ActionEvent e) {
        // update original state to selected button
        _box.setSelectedItem(e.getActionCommand());
    }

    void originalActionPerformed(java.awt.event.ActionEvent e) {
        // update this state to original state if there's a button
        // that corresponds
        while (_box.getSelectedIndex()+1>=v.size()) {
            // oops - box has grown; add buttons!
            JRadioButton b;
            v.addElement(b = new JRadioButton("Reserved value "+v.size()));
            g.add(b);
        }
        v.elementAt(_box.getSelectedIndex()).setSelected(true);
    }

    void originalPropertyChanged(java.beans.PropertyChangeEvent e) {
        // update this color from original state
        if (e.getPropertyName().equals("State")) {
            if (log.isDebugEnabled()) log.debug("State change seen");
                setColor();
        }
    }

    protected void setColor() {
        for (int i = 0; i<v.size(); i++) {
            v.elementAt(i).setBackground(_value.getBackground());
        }
    }

    /**
     * Setting tooltip both on this panel, and all
     * buttons inside
     */
    public void setToolTipText(String t) {
        super.setToolTipText(t);   // do default stuff
        // include all buttons
        for (JRadioButton b : b1) {
            b.setToolTipText(t);
        }
    }

    JRadioButton b1[];
    transient ActionListener l1[];
    transient ActionListener l2;
    transient PropertyChangeListener p1;

    VariableValue _var = null;
    JComboBox _box = null;
    JComboBox _value = null;
    Vector<JRadioButton> v = new Vector<JRadioButton>();

    public void dispose() {
        for (int i = 0; i<l1.length; i++) {
                b1[i].removeActionListener(l1[i]);
            }
        _box.removeActionListener(l2);
        _var.removePropertyChangeListener(p1);
        _var = null;
        _box = null;
    }

    // initialize logging
    static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(ComboRadioButtons.class.getName());

}
