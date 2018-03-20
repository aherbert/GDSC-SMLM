package gdsc.smlm.gui;

import java.awt.Component;
import java.awt.EventQueue;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

import gdsc.smlm.results.ArrayPeakResultStore;
import gdsc.smlm.results.ExtendedPeakResult;
import gdsc.smlm.results.PeakResult;
import gdsc.smlm.results.PeakResultStoreList;

public class PeakResultListFrame extends JFrame
{
	private static final long serialVersionUID = -1530205032042929260L;

	private class MyCellRenderer extends JLabel implements ListCellRenderer<PeakResult>
	{
		// This is the only method defined by ListCellRenderer.
		// We just reconfigure the JLabel each time we're called.
		private static final long serialVersionUID = 1998620838894273028L;

		public Component getListCellRendererComponent(JList<? extends PeakResult> list, // the list
				PeakResult value, // value to display
				int index, // cell index
				boolean isSelected, // is the cell selected
				boolean cellHasFocus) // does the cell have focus
		{
			// TODO - Make this a better representation of the Peak Result.
			// Build a configurable layout using the TableResults settings.
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < PeakResult.STANDARD_PARAMETERS; i++)
			{
				if (sb.length() != 0)
					sb.append(' ');
				sb.append(PeakResult.getParameterName(i)).append('=').append(value.getParameter(i));
			}

			String s = sb.toString();
			setText(s);
			if (isSelected)
			{
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else
			{
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			return this;
		}
	}

	private JList<PeakResult> list;

	public PeakResultListFrame(PeakResultModel model)
	{
		list = new JList<PeakResult>(model);
		list.setPrototypeCellValue(new ExtendedPeakResult(1, 1, 1, 1));
		list.setCellRenderer(new MyCellRenderer());
		final JScrollPane scroll = new JScrollPane(list);
		add(scroll);
		pack();

	}

	public void addListSelectionListener(ListSelectionListener listener)
	{
		list.addListSelectionListener(listener);
	}

	public void removeListSelectionListener(ListSelectionListener listener)
	{
		list.removeListSelectionListener(listener);
	}

	/**
	 * Launch the application.
	 */
	public static void main(String[] args)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				try
				{
					RandomGenerator r = new Well19937c();
					PeakResultStoreList store = new ArrayPeakResultStore(10);
					for (int i = 10; i-- > 0;)
					{
						store.add(new PeakResult(r.nextInt(), r.nextInt(), r.nextInt(), r.nextFloat(), r.nextDouble(),
								r.nextFloat(), PeakResult.createParams(r.nextFloat(), r.nextFloat(), r.nextFloat(),
										r.nextFloat(), r.nextFloat()),
								null));
					}
					PeakResultModel model = new PeakResultModel(store);

					final PeakResultListFrame d = new PeakResultListFrame(model);
					d.addListSelectionListener(new ListSelectionListener()
					{
						public void valueChanged(ListSelectionEvent e)
						{
							// Only process the event if the value is not adjusting.
							// Then to determine what has changed only process the 
							// indices between the first and last index. 

							if (e.getValueIsAdjusting())
								return;
							System.out.printf("D Selected %d-%d [%b] : %s\n", e.getFirstIndex(), e.getLastIndex(),
									e.getValueIsAdjusting(), Arrays.toString(d.list.getSelectedIndices()));
						}
					});
					d.setDefaultCloseOperation(EXIT_ON_CLOSE);
					d.setVisible(true);

					// Selecting in one list activates the other list

					final PeakResultListFrame d2 = new PeakResultListFrame(model);
					d2.addListSelectionListener(new ListSelectionListener()
					{
						public void valueChanged(ListSelectionEvent e)
						{
							if (e.getValueIsAdjusting())
								return;
							int[] indices = d2.list.getSelectedIndices();
							System.out.printf("D2 Selected %d-%d [%b] : %s\n", e.getFirstIndex(), e.getLastIndex(),
									e.getValueIsAdjusting(), Arrays.toString(indices));
							d.list.setSelectedIndices(indices);
						}
					});
					d2.setDefaultCloseOperation(EXIT_ON_CLOSE);
					d2.setVisible(true);

					ListSelectionModel selectionModel = d.list.getSelectionModel();

					// Random selections ...

				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		});
	}
}