import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Шифр «квадратичная (поворотная) решётка» — она же решётка Кардано / Флейснера.
 *
 * Запуск GUI:      java CardanGrille
 * Проверка логики: java CardanGrille --selftest
 */
public class CardanGrille {

    public static void main(String[] args) {
        if (args.length > 0 && "--selftest".equals(args[0])) {
            SelfTest.run();
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new MainWindow().setVisible(true);
            }
        });
    }
}

/* ====================================================================== */
/*  МОДЕЛЬ: сама решётка                                                  */
/* ====================================================================== */

/**
 * Квадратная решётка размера N x N (N — чётное) с N*N/4 вырезанными окошками.
 *
 * Ключевое свойство: при четырёх поворотах на 90° окошки должны накрыть
 * каждую клетку квадрата ровно один раз. Чтобы это свойство нельзя было
 * нарушить, решётка хранится не как «произвольный набор дырок», а как выбор
 * одной клетки из каждой «четвёрки» (орбиты поворота).
 */
class Grille {

    final int size;
    private final boolean[][] holes;

    Grille(int size) {
        if (size < 2 || size % 2 != 0) {
            throw new IllegalArgumentException("Размер решётки должен быть чётным числом, не меньше 2");
        }
        this.size = size;
        this.holes = new boolean[size][size];
        // по умолчанию — все окошки в левом верхнем квадранте (это корректная решётка)
        for (int r = 0; r < size / 2; r++) {
            for (int c = 0; c < size / 2; c++) {
                holes[r][c] = true;
            }
        }
    }

    boolean isHole(int r, int c) {
        return holes[r][c];
    }

    /** Сколько окошек должно быть у корректной решётки. */
    int holeCount() {
        return size * size / 4;
    }

    /** Куда переедет клетка (r, c) после поворота квадрата на 90° по часовой стрелке. */
    int[] rotate(int r, int c) {
        return new int[]{c, size - 1 - r};
    }

    /** Четвёрка клеток, которые переходят друг в друга при поворотах. */
    int[][] orbit(int r, int c) {
        int[][] res = new int[4][];
        int rr = r, cc = c;
        for (int k = 0; k < 4; k++) {
            res[k] = new int[]{rr, cc};
            int[] p = rotate(rr, cc);
            rr = p[0];
            cc = p[1];
        }
        return res;
    }

    /** Сделать клетку окошком; остальные три клетки её четвёрки закрываются. */
    void setHole(int r, int c) {
        int[][] orb = orbit(r, c);
        for (int k = 0; k < 4; k++) {
            holes[orb[k][0]][orb[k][1]] = false;
        }
        holes[r][c] = true;
    }

    /**
     * Позиции окошек после turns поворотов по часовой стрелке,
     * отсортированные в порядке чтения: сверху вниз, слева направо.
     */
    List<int[]> holePositions(int turns) {
        List<int[]> res = new ArrayList<int[]>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (!holes[r][c]) continue;
                int rr = r, cc = c;
                for (int i = 0; i < turns; i++) {
                    int[] p = rotate(rr, cc);
                    rr = p[0];
                    cc = p[1];
                }
                res.add(new int[]{rr, cc});
            }
        }
        Collections.sort(res, new Comparator<int[]>() {
            public int compare(int[] a, int[] b) {
                return a[0] != b[0] ? a[0] - b[0] : a[1] - b[1];
            }
        });
        return res;
    }

    /** Проверка: каждая клетка накрывается окошком ровно один раз за четыре поворота. */
    boolean isValid() {
        int[][] cover = new int[size][size];
        for (int k = 0; k < 4; k++) {
            for (int[] p : holePositions(k)) {
                cover[p[0]][p[1]]++;
            }
        }
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (cover[r][c] != 1) return false;
            }
        }
        return true;
    }

    /**
     * Ключ в виде строки «размер:цифры». Для каждой клетки левого верхнего
     * квадранта записывается цифра 0..3 — на сколько поворотов сдвинуто
     * окошко этой четвёрки. Пример: 6:012303120
     */
    String toKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(size).append(':');
        for (int r = 0; r < size / 2; r++) {
            for (int c = 0; c < size / 2; c++) {
                int[][] orb = orbit(r, c);
                int k = 0;
                while (k < 4 && !holes[orb[k][0]][orb[k][1]]) k++;
                sb.append(k == 4 ? '?' : (char) ('0' + k));
            }
        }
        return sb.toString();
    }

    static Grille fromKey(String key) {
        String s = key == null ? "" : key.trim();
        int idx = s.indexOf(':');
        if (idx < 0) {
            throw new IllegalArgumentException("Ключ должен выглядеть как «размер:цифры», например 6:012303120");
        }
        int n;
        try {
            n = Integer.parseInt(s.substring(0, idx).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Не удалось прочитать размер решётки в ключе");
        }
        if (n < 2 || n % 2 != 0) {
            throw new IllegalArgumentException("Размер решётки должен быть чётным числом");
        }
        String digits = s.substring(idx + 1).replaceAll("\\s+", "");
        int need = (n / 2) * (n / 2);
        if (digits.length() != need) {
            throw new IllegalArgumentException("Для решётки " + n + "×" + n + " нужно ровно "
                    + need + " цифр, а в ключе их " + digits.length());
        }
        Grille g = new Grille(n);
        int i = 0;
        for (int r = 0; r < n / 2; r++) {
            for (int c = 0; c < n / 2; c++) {
                char ch = digits.charAt(i++);
                if (ch < '0' || ch > '3') {
                    throw new IllegalArgumentException("В ключе допустимы только цифры 0, 1, 2, 3");
                }
                int[] p = g.orbit(r, c)[ch - '0'];
                g.setHole(p[0], p[1]);
            }
        }
        return g;
    }

    static Grille random(int size, Random rnd) {
        Grille g = new Grille(size);
        for (int r = 0; r < size / 2; r++) {
            for (int c = 0; c < size / 2; c++) {
                int[] p = g.orbit(r, c)[rnd.nextInt(4)];
                g.setHole(p[0], p[1]);
            }
        }
        return g;
    }
}

/* ====================================================================== */
/*  МОДЕЛЬ: сам шифр                                                      */
/* ====================================================================== */

final class GrilleCipher {

    private GrilleCipher() {
    }

    /** Дополняет текст символом-заполнителем до целого числа блоков N*N. */
    static String pad(String text, int blockLen, char filler) {
        StringBuilder sb = new StringBuilder(text);
        if (sb.length() == 0) sb.append(filler);
        while (sb.length() % blockLen != 0) sb.append(filler);
        return sb.toString();
    }

    /** Заполняет квадрат одним блоком текста через четыре положения решётки. */
    static char[][] fillSquare(String block, Grille g) {
        int n = g.size;
        char[][] sq = new char[n][n];
        int i = 0;
        for (int k = 0; k < 4; k++) {
            for (int[] p : g.holePositions(k)) {
                sq[p[0]][p[1]] = block.charAt(i++);
            }
        }
        return sq;
    }

    /** Карта «какой поворот (0..3) записал эту клетку» — нужна для наглядной демонстрации. */
    static int[][] stepMap(Grille g) {
        int n = g.size;
        int[][] m = new int[n][n];
        for (int k = 0; k < 4; k++) {
            for (int[] p : g.holePositions(k)) {
                m[p[0]][p[1]] = k;
            }
        }
        return m;
    }

    static String encrypt(String text, Grille g, char filler) {
        int n = g.size;
        int blockLen = n * n;
        String src = pad(text, blockLen, filler);
        StringBuilder out = new StringBuilder(src.length());
        for (int off = 0; off < src.length(); off += blockLen) {
            char[][] sq = fillSquare(src.substring(off, off + blockLen), g);
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    out.append(sq[r][c]);
                }
            }
        }
        return out.toString();
    }

    static String decrypt(String cipher, Grille g, char filler) {
        int n = g.size;
        int blockLen = n * n;
        if (cipher.length() % blockLen != 0) {
            throw new IllegalArgumentException("Длина шифртекста (" + cipher.length()
                    + ") должна делиться на " + blockLen + " = " + n + "×" + n
                    + ". Похоже, взят не тот размер решётки или текст повреждён.");
        }
        StringBuilder out = new StringBuilder(cipher.length());
        for (int off = 0; off < cipher.length(); off += blockLen) {
            char[][] sq = new char[n][n];
            int i = off;
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    sq[r][c] = cipher.charAt(i++);
                }
            }
            for (int k = 0; k < 4; k++) {
                for (int[] p : g.holePositions(k)) {
                    out.append(sq[p[0]][p[1]]);
                }
            }
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == filler) end--;
        return out.substring(0, end);
    }
}

/* ====================================================================== */
/*  Проверка логики без GUI                                               */
/* ====================================================================== */

final class SelfTest {

    static void run() {
        Random rnd = new Random(20260907L);
        int failed = 0;
        String[] samples = {
                "ВСТРЕЧАЕМСЯ В ПОЛНОЧЬ У СТАРОГО ДУБА",
                "a",
                "Привет, мир! 1234 — проверка знаков препинания.",
                "Съешь же ещё этих мягких французских булок да выпей чаю"
        };
        for (int size = 4; size <= 12; size += 2) {
            for (int attempt = 0; attempt < 200; attempt++) {
                Grille g = Grille.random(size, rnd);
                if (!g.isValid()) {
                    System.out.println("FAIL: некорректная решётка " + size + " " + g.toKey());
                    failed++;
                }
                if (!Grille.fromKey(g.toKey()).toKey().equals(g.toKey())) {
                    System.out.println("FAIL: ключ не восстанавливается: " + g.toKey());
                    failed++;
                }
                String text = samples[attempt % samples.length];
                String enc = GrilleCipher.encrypt(text, g, '·');
                String dec = GrilleCipher.decrypt(enc, g, '·');
                if (!dec.equals(text)) {
                    System.out.println("FAIL: " + size + " \"" + text + "\" -> \"" + dec + "\"");
                    failed++;
                }
                if (enc.length() % (size * size) != 0) {
                    System.out.println("FAIL: длина шифртекста не кратна блоку");
                    failed++;
                }
            }
        }
        // пример из справки, вручную
        Grille g = Grille.fromKey("4:0123");
        System.out.println("Пример решётки 4x4, ключ " + g.toKey() + ", корректна: " + g.isValid());
        for (int r = 0; r < 4; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < 4; c++) sb.append(g.isHole(r, c) ? " []" : " ##");
            System.out.println(sb);
        }
        String demo = "КРИПТОГРАФИЯ";
        String enc = GrilleCipher.encrypt(demo, g, '·');
        System.out.println("Открытый текст: " + demo);
        System.out.println("Шифртекст:      " + enc);
        System.out.println("Расшифровано:   " + GrilleCipher.decrypt(enc, g, '·'));
        System.out.println(failed == 0 ? "\nВСЕ ТЕСТЫ ПРОЙДЕНЫ" : "\nОШИБОК: " + failed);
    }
}

/* ====================================================================== */
/*  GUI: редактор решётки                                                 */
/* ====================================================================== */

/** Кликабельная картинка решётки. Клик по клетке делает её окошком. */
class GrillePanel extends JPanel {

    private static final Color CARD = new Color(0x54, 0x6E, 0x7A);
    private static final Color HOLE = Color.WHITE;
    private static final Color HINT = new Color(0x90, 0xCA, 0xF9);

    private Grille grille;
    private Runnable onChange;
    private int hoverR = -1, hoverC = -1;
    private int cell = 34;

    GrillePanel(Grille g) {
        this.grille = g;
        setBackground(new Color(0xFA, 0xFA, 0xFA));
        setToolTipText("Клик по клетке — сделать её окошком (три остальные клетки этой четвёрки закроются)");
        MouseAdapter ma = new MouseAdapter() {
            public void mouseMoved(MouseEvent e) {
                int[] p = cellAt(e.getX(), e.getY());
                int nr = p == null ? -1 : p[0];
                int nc = p == null ? -1 : p[1];
                if (nr != hoverR || nc != hoverC) {
                    hoverR = nr;
                    hoverC = nc;
                    repaint();
                }
            }

            public void mouseExited(MouseEvent e) {
                hoverR = hoverC = -1;
                repaint();
            }

            public void mousePressed(MouseEvent e) {
                int[] p = cellAt(e.getX(), e.getY());
                if (p != null) {
                    grille.setHole(p[0], p[1]);
                    repaint();
                    if (onChange != null) onChange.run();
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        updateSize();
    }

    void setOnChange(Runnable r) {
        this.onChange = r;
    }

    void setGrille(Grille g) {
        this.grille = g;
        hoverR = hoverC = -1;
        updateSize();
        revalidate();
        repaint();
    }

    private void updateSize() {
        cell = grille.size >= 12 ? 26 : grille.size >= 10 ? 30 : 34;
        Dimension d = new Dimension(grille.size * cell + 2, grille.size * cell + 2);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
    }

    private int ox() {
        return (getWidth() - grille.size * cell) / 2;
    }

    private int oy() {
        return (getHeight() - grille.size * cell) / 2;
    }

    private int[] cellAt(int x, int y) {
        int c = (x - ox()) / cell;
        int r = (y - oy()) / cell;
        if (x < ox() || y < oy() || r < 0 || c < 0 || r >= grille.size || c >= grille.size) return null;
        return new int[]{r, c};
    }

    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g2 = (Graphics2D) g0.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int n = grille.size, ox = ox(), oy = oy();

        int[][] hoverOrbit = (hoverR >= 0) ? grille.orbit(hoverR, hoverC) : null;

        // нумерация окошек в порядке заполнения
        java.util.Map<Integer, Integer> order = new java.util.HashMap<Integer, Integer>();
        int num = 1;
        for (int[] p : grille.holePositions(0)) {
            order.put(p[0] * n + p[1], num++);
        }

        g2.setFont(new Font("SansSerif", Font.PLAIN, Math.max(9, cell / 3)));
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int x = ox + c * cell, y = oy + r * cell;
                boolean hole = grille.isHole(r, c);
                g2.setColor(hole ? HOLE : CARD);
                g2.fillRect(x, y, cell, cell);
                if (hoverOrbit != null && !hole) {
                    for (int k = 0; k < 4; k++) {
                        if (hoverOrbit[k][0] == r && hoverOrbit[k][1] == c) {
                            g2.setColor(new Color(HINT.getRed(), HINT.getGreen(), HINT.getBlue(), 130));
                            g2.fillRect(x, y, cell, cell);
                        }
                    }
                }
                if (hole) {
                    Integer idx = order.get(r * n + c);
                    if (idx != null) {
                        g2.setColor(new Color(0x90, 0xA4, 0xAE));
                        String s = String.valueOf(idx);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(s, x + (cell - fm.stringWidth(s)) / 2, y + (cell + fm.getAscent()) / 2 - 1);
                    }
                }
            }
        }

        // сетка
        g2.setColor(new Color(0, 0, 0, 45));
        for (int i = 0; i <= n; i++) {
            g2.drawLine(ox, oy + i * cell, ox + n * cell, oy + i * cell);
            g2.drawLine(ox + i * cell, oy, ox + i * cell, oy + n * cell);
        }
        g2.setColor(new Color(0x37, 0x47, 0x4F));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(ox, oy, n * cell, n * cell);
        g2.dispose();
    }
}

/* ====================================================================== */
/*  GUI: пошаговая демонстрация                                           */
/* ====================================================================== */

class DemoPanel extends JPanel {

    static final Color[] STEP_COLORS = {
            new Color(0x15, 0x65, 0xC0), new Color(0x2E, 0x7D, 0x32),
            new Color(0xE6, 0x51, 0x00), new Color(0x6A, 0x1B, 0x9A)};

    private Grille grille = Grille.random(6, new Random(7));
    private char filler = '·';
    private String padded = "";
    private String block = "";
    private char[][] square;
    private int[][] stepMap;
    private int step = 0;

    private final JLabel head = new JLabel();
    private final JLabel note = new JLabel();
    private final JSlider slider = new JSlider(0, 4, 0);
    private final JSpinner blockSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
    private final SquareView view = new SquareView();

    DemoPanel() {
        super(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setBackground(Color.WHITE);

        head.setFont(head.getFont().deriveFont(Font.BOLD, 15f));
        head.setBorder(new EmptyBorder(0, 0, 6, 0));

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(head, BorderLayout.NORTH);
        north.add(note, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(view, BorderLayout.CENTER);

        slider.setMajorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setSnapToTicks(true);
        slider.setOpaque(false);
        java.util.Hashtable<Integer, JLabel> labels = new java.util.Hashtable<Integer, JLabel>();
        labels.put(0, new JLabel("0°"));
        labels.put(1, new JLabel("90°"));
        labels.put(2, new JLabel("180°"));
        labels.put(3, new JLabel("270°"));
        labels.put(4, new JLabel("итог"));
        slider.setLabelTable(labels);
        slider.setPaintLabels(true);
        slider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent e) {
                step = slider.getValue();
                refresh();
            }
        });

        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        prev.addActionListener(e -> slider.setValue(Math.max(0, slider.getValue() - 1)));
        next.addActionListener(e -> slider.setValue(Math.min(4, slider.getValue() + 1)));

        blockSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent e) {
                rebuild();
            }
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        south.setOpaque(false);
        south.add(prev);
        south.add(slider);
        south.add(next);
        south.add(new JLabel("   блок №"));
        blockSpinner.setPreferredSize(new Dimension(60, 26));
        south.add(blockSpinner);
        add(south, BorderLayout.SOUTH);

        setData(grille, "ВСТРЕЧАЕМСЯ В ПОЛНОЧЬ У СТАРОГО ДУБА", filler);
    }

    void setData(Grille g, String text, char filler) {
        this.grille = g;
        this.filler = filler;
        int blockLen = g.size * g.size;
        this.padded = GrilleCipher.pad(text == null || text.isEmpty() ? "ПРИМЕР ТЕКСТА" : text, blockLen, filler);
        int blocks = padded.length() / blockLen;
        SpinnerNumberModel m = (SpinnerNumberModel) blockSpinner.getModel();
        m.setMaximum(Integer.valueOf(blocks));
        if (((Integer) blockSpinner.getValue()).intValue() > blocks) blockSpinner.setValue(Integer.valueOf(1));
        rebuild();
    }

    private void rebuild() {
        int blockLen = grille.size * grille.size;
        int bi = ((Integer) blockSpinner.getValue()).intValue() - 1;
        int off = Math.min(bi * blockLen, Math.max(0, padded.length() - blockLen));
        block = padded.substring(off, off + blockLen);
        square = GrilleCipher.fillSquare(block, grille);
        stepMap = GrilleCipher.stepMap(grille);
        refresh();
    }

    private void refresh() {
        int per = grille.holeCount();
        if (step < 4) {
            head.setText("Шаг " + (step + 1) + " из 4 — решётка повёрнута на " + (step * 90) + "°");
            String piece = block.substring(step * per, (step + 1) * per);
            note.setText("<html><body style='width:520px'>Через открытые окошки вписываем следующие "
                    + per + " символов (слева направо, сверху вниз):<br><b><span style='font-size:14px'>"
                    + esc(piece) + "</span></b><br><span style='color:#666'>Серым просвечивают буквы, "
                    + "записанные на прошлых шагах — они уже лежат на бумаге под картоном.</span></body></html>");
        } else {
            head.setText("Готово — решётку снимаем");
            StringBuilder sb = new StringBuilder();
            for (int r = 0; r < grille.size; r++)
                for (int c = 0; c < grille.size; c++) sb.append(square[r][c]);
            note.setText("<html><body style='width:520px'>Весь квадрат заполнен. Выписываем его по строкам "
                    + "слева направо — это шифртекст блока:<br><b><span style='font-size:14px'>"
                    + esc(sb.toString()) + "</span></b><br><span style='color:#666'>Цвет буквы показывает, "
                    + "на каком повороте она была записана.</span></body></html>");
        }
        view.repaint();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace(" ", "&nbsp;");
    }

    private class SquareView extends JComponent {

        SquareView() {
            setPreferredSize(new Dimension(460, 400));
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g2 = (Graphics2D) g0.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int n = grille.size;
            int cell = Math.max(20, Math.min(54, (Math.min(getWidth(), getHeight()) - 16) / n));
            int ox = (getWidth() - n * cell) / 2, oy = (getHeight() - n * cell) / 2;

            g2.setColor(Color.WHITE);
            g2.fillRect(ox, oy, n * cell, n * cell);

            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(11, cell / 2)));
            FontMetrics fm = g2.getFontMetrics();
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    int s = stepMap[r][c];
                    if (step < 4 && s > step) continue;
                    Color col = STEP_COLORS[s];
                    if (step < 4 && s < step) col = new Color(col.getRed(), col.getGreen(), col.getBlue(), 110);
                    g2.setColor(col);
                    String ch = String.valueOf(square[r][c]);
                    if (ch.equals(" ")) ch = "␣";
                    g2.drawString(ch, ox + c * cell + (cell - fm.stringWidth(ch)) / 2,
                            oy + r * cell + (cell + fm.getAscent()) / 2 - 2);
                }
            }

            if (step < 4) {
                g2.setColor(new Color(0x78, 0x90, 0x9C, 228));
                for (int r = 0; r < n; r++) {
                    for (int c = 0; c < n; c++) {
                        if (stepMap[r][c] != step) g2.fillRect(ox + c * cell, oy + r * cell, cell, cell);
                    }
                }
            }

            g2.setColor(new Color(0, 0, 0, 45));
            for (int i = 0; i <= n; i++) {
                g2.drawLine(ox, oy + i * cell, ox + n * cell, oy + i * cell);
                g2.drawLine(ox + i * cell, oy, ox + i * cell, oy + n * cell);
            }
            g2.setColor(new Color(0x37, 0x47, 0x4F));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(ox, oy, n * cell, n * cell);
            g2.dispose();
        }
    }
}

/* ====================================================================== */
/*  GUI: главное окно                                                     */
/* ====================================================================== */

class MainWindow extends JFrame {

    private final Random rnd = new Random();
    private Grille grille = Grille.random(6, rnd);
    private final GrillePanel grillePanel = new GrillePanel(grille);
    private final DemoPanel demoPanel = new DemoPanel();

    private final JTextArea inputArea = new JTextArea(9, 44);
    private final JTextArea outputArea = new JTextArea(9, 44);
    private final JTextField keyField = new JTextField(16);
    private final JTextField fillerField = new JTextField("·", 3);
    private final JComboBox<String> sizeBox =
            new JComboBox<String>(new String[]{"4 × 4", "6 × 6", "8 × 8", "10 × 10", "12 × 12"});
    private final JLabel status = new JLabel(" ");
    private final JTabbedPane tabs = new JTabbedPane();
    private boolean suppressSizeEvent = false;

    MainWindow() {
        super("Шифр «квадратичная решётка» (решётка Кардано)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        tabs.addTab("Шифрование", buildCryptoTab());
        tabs.addTab("Как это работает", demoPanel);
        tabs.addTab("Справка о шифре", buildHelpTab());
        tabs.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent e) {
                if (tabs.getSelectedIndex() == 1) {
                    demoPanel.setData(grille, inputArea.getText(), filler());
                }
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.add(tabs, BorderLayout.CENTER);
        status.setBorder(new EmptyBorder(4, 10, 6, 10));
        status.setForeground(new Color(0x45, 0x5A, 0x64));
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);

        keyField.setText(grille.toKey());
        sizeBox.setSelectedIndex(1);
        sizeBox.addActionListener(e -> {
            if (suppressSizeEvent) return;
            int n = 4 + 2 * sizeBox.getSelectedIndex();
            if (n != grille.size) {
                setGrille(Grille.random(n, rnd), "Создана случайная решётка " + n + "×" + n);
            }
        });
        grillePanel.setOnChange(new Runnable() {
            public void run() {
                keyField.setText(grille.toKey());
                say("Решётка изменена, новый ключ: " + grille.toKey());
            }
        });

        pack();
        setMinimumSize(new Dimension(900, 620));
        setLocationRelativeTo(null);
        say("Готово к работе. Текущий ключ: " + grille.toKey());
    }

    /* ---------- вкладка «Шифрование» ---------- */

    private JComponent buildCryptoTab() {
        JPanel tab = new JPanel(new BorderLayout(12, 12));
        tab.setBorder(new EmptyBorder(12, 12, 12, 12));

        /* --- левая колонка: ключ-решётка --- */
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sizeRow.add(new JLabel("Размер:"));
        sizeRow.add(sizeBox);
        JButton randomBtn = new JButton("Случайная");
        randomBtn.addActionListener(e -> setGrille(Grille.random(grille.size, rnd), "Сгенерирована случайная решётка"));
        sizeRow.add(randomBtn);
        sizeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, sizeRow.getPreferredSize().height));
        left.add(sizeRow);
        left.add(Box.createVerticalStrut(6));

        JPanel gp = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        gp.add(grillePanel);
        gp.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(gp);
        left.add(Box.createVerticalStrut(6));

        JLabel hint = new JLabel("<html><body style='width:250px;color:#607D8B'>"
                + "Белые клетки — вырезанные окошки, цифра в них показывает порядок заполнения. "
                + "Клик по любой клетке переносит окошко туда (подсвеченные три клетки — та же «четвёрка»). "
                + "Решётка всегда остаётся корректной.</body></html>");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(hint);
        left.add(Box.createVerticalStrut(8));

        JPanel keyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        keyRow.add(new JLabel("Ключ:"));
        keyField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        keyRow.add(keyField);
        keyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        keyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, keyRow.getPreferredSize().height));
        left.add(keyRow);

        JPanel keyBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton applyKey = new JButton("Применить ключ");
        applyKey.addActionListener(e -> {
            try {
                Grille g = Grille.fromKey(keyField.getText());
                suppressSizeEvent = true;
                sizeBox.setSelectedIndex(Math.max(0, (g.size - 4) / 2));
                suppressSizeEvent = false;
                setGrille(g, "Ключ применён");
            } catch (IllegalArgumentException ex) {
                error(ex.getMessage());
                keyField.setText(grille.toKey());
            }
        });
        JButton copyKey = new JButton("Копировать");
        copyKey.addActionListener(e -> {
            toClipboard(grille.toKey());
            say("Ключ скопирован в буфер обмена");
        });
        keyBtns.add(applyKey);
        keyBtns.add(copyKey);
        keyBtns.setAlignmentX(Component.LEFT_ALIGNMENT);
        keyBtns.setMaximumSize(new Dimension(Integer.MAX_VALUE, keyBtns.getPreferredSize().height));
        left.add(keyBtns);
        left.add(Box.createVerticalStrut(10));

        JPanel fillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fillRow.add(new JLabel("Заполнитель:"));
        fillerField.setToolTipText("Символ, которым добивается последний неполный блок. "
                + "При расшифровке он отбрасывается с конца.");
        fillRow.add(fillerField);
        fillRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fillRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, fillRow.getPreferredSize().height));
        left.add(fillRow);

        JLabel fillHint = new JLabel("<html><body style='width:250px;color:#607D8B'>"
                + "Этим символом добивается последний неполный блок; при расшифровке он "
                + "отбрасывается с конца. Выберите то, чего в тексте заведомо нет.</body></html>");
        fillHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(fillHint);
        left.add(Box.createVerticalGlue());

        JPanel leftWrap = new JPanel(new BorderLayout());
        leftWrap.setBorder(BorderFactory.createTitledBorder("Ключ — решётка"));
        leftWrap.add(left, BorderLayout.NORTH);
        tab.add(leftWrap, BorderLayout.WEST);

        /* --- центр: текстовые поля --- */
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        inputArea.setFont(mono);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setText("Встречаемся в полночь у старого дуба");
        outputArea.setFont(mono);
        outputArea.setLineWrap(true);
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(0xF7, 0xF9, 0xFA));

        JScrollPane inScroll = new JScrollPane(inputArea);
        inScroll.setBorder(BorderFactory.createTitledBorder("Исходный текст (или шифртекст для расшифровки)"));
        JScrollPane outScroll = new JScrollPane(outputArea);
        outScroll.setBorder(BorderFactory.createTitledBorder("Результат"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inScroll, outScroll);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        tab.add(split, BorderLayout.CENTER);

        /* --- низ: кнопки --- */
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton enc = new JButton("Зашифровать →");
        enc.setFont(enc.getFont().deriveFont(Font.BOLD));
        enc.addActionListener(e -> doEncrypt());
        JButton dec = new JButton("Расшифровать →");
        dec.setFont(dec.getFont().deriveFont(Font.BOLD));
        dec.addActionListener(e -> doDecrypt());
        JButton swap = new JButton("↑ Результат в поле ввода");
        swap.addActionListener(e -> {
            inputArea.setText(outputArea.getText());
            outputArea.setText("");
            say("Результат перенесён в поле ввода");
        });
        JButton copyOut = new JButton("Копировать результат");
        copyOut.addActionListener(e -> {
            toClipboard(outputArea.getText());
            say("Результат скопирован в буфер обмена");
        });
        JButton clear = new JButton("Очистить");
        clear.addActionListener(e -> {
            inputArea.setText("");
            outputArea.setText("");
            say("Поля очищены");
        });
        south.add(enc);
        south.add(dec);
        south.add(Box.createHorizontalStrut(12));
        south.add(swap);
        south.add(copyOut);
        south.add(clear);
        tab.add(south, BorderLayout.SOUTH);

        return tab;
    }

    /* ---------- вкладка «Справка» ---------- */

    private JComponent buildHelpTab() {
        StringBuilder h = new StringBuilder();
        h.append("<html><body style='font-family:sans-serif;font-size:12px;margin:14px;max-width:780px'>");
        h.append("<h2>Квадратичная (поворотная) решётка</h2>");
        h.append("<p>Это <b>шифр перестановки</b>: буквы текста остаются теми же самыми, но меняются местами. ");
        h.append("Придумал его итальянец Джероламо Кардано (XVI век), а привычный «вращающийся» вид ему придал ");
        h.append("австрийский полковник Эдуард Флейснер фон Востровиц (1881). Такой шифр использовался в армиях ");
        h.append("Германии и Австро-Венгрии в Первую мировую войну.</p>");

        h.append("<h3 style='font-size:15px'>Что такое решётка</h3>");
        h.append("<p>Берём квадрат N×N клеток (N обязательно чётное) — это лист бумаги. Сверху кладём такой же ");
        h.append("квадрат из плотного картона, в котором вырезано <b>N²/4</b> окошек. Например, для 6×6: 36 клеток, ");
        h.append("9 окошек.</p>");
        h.append("<p>Окошки вырезаны не как попало. Разобьём все клетки квадрата на «четвёрки»: клетка, и три её ");
        h.append("положения после поворотов на 90°, 180° и 270°. Таких четвёрок ровно N²/4. ");
        h.append("<b>Правило: из каждой четвёрки вырезается ровно одно окошко.</b> Тогда при четырёх поворотах ");
        h.append("решётки окошки пройдут по каждой клетке квадрата ровно один раз — ни одна клетка не останется ");
        h.append("пустой и ни одна не будет заполнена дважды. В программе это правило соблюдается автоматически.</p>");

        h.append("<h3 style='font-size:15px'>Как шифруем</h3>");
        h.append("<ol>");
        h.append("<li>Кладём решётку на чистый лист и вписываем в окошки первые N²/4 букв текста — слева направо, сверху вниз.</li>");
        h.append("<li>Поворачиваем решётку на 90° по часовой стрелке. Открылись другие клетки — вписываем следующие N²/4 букв.</li>");
        h.append("<li>Ещё поворот (180°) — следующая порция букв.</li>");
        h.append("<li>Ещё поворот (270°) — последняя порция. Квадрат заполнен целиком.</li>");
        h.append("<li>Снимаем решётку и выписываем весь квадрат <b>построчно</b>. Это и есть шифртекст.</li>");
        h.append("</ol>");
        h.append("<p>Если текст длиннее N² символов, он режется на блоки по N² символов, и каждый блок шифруется ");
        h.append("той же решёткой. Последний блок добивается символом-заполнителем.</p>");

        h.append("<h3 style='font-size:15px'>Как расшифровываем</h3>");
        h.append("<p>Всё наоборот: выписываем шифртекст в пустой квадрат построчно, накладываем ту же решётку в ");
        h.append("исходном положении и читаем буквы в окошках, потом поворачиваем — и так четыре раза. ");
        h.append("Ключ у отправителя и получателя должен быть одинаковый: и сама решётка, и её стартовое положение, ");
        h.append("и направление поворота.</p>");

        h.append("<h3 style='font-size:15px'>Настоящий пример: решётка 4×4, ключ 4:0123</h3>");
        h.append("<p>Шифруем слово КРИПТОГРАФИЯ (12 букв). В блок 4×4 влезает 16 символов, поэтому текст ");
        h.append("добивается четырьмя заполнителями «·». За каждый поворот вписываем по 16/4 = 4 символа.</p>");
        h.append("<pre style='font-size:13px;line-height:1.4'>");
        h.append("  решётка       шаг 1: КРИП   шаг 2: ТОГР   шаг 3: АФИЯ   шаг 4: ····\n");
        h.append("  □ ■ ■ ■         К . . .       К . . Т       К . . Т       К · · Т\n");
        h.append("  ■ ■ ■ □         . . . Р       . О . Р       А О Ф Р       А О Ф Р\n");
        h.append("  ■ □ ■ □         . И . П       . И . П       И И . П       И И · П\n");
        h.append("  ■ ■ ■ ■         . . . .       . Г Р .       . Г Р Я       · Г Р Я\n");
        h.append("</pre>");
        h.append("<p>Готовый квадрат читаем по строкам: <b>К··ТАОФРИИ·П·ГРЯ</b> — это и есть шифртекст. ");
        h.append("Буквы те же самые, изменился только их порядок.</p>");

        h.append("<h3 style='font-size:15px'>Ключ и его размер</h3>");
        h.append("<p>Ключ — это расположение окошек. В программе он записывается строкой вида ");
        h.append("<code>6:012303120</code>: до двоеточия размер решётки, после — по одной цифре 0–3 на каждую ");
        h.append("четвёрку (на сколько поворотов сдвинуто её окошко). Количество вариантов решётки — 4<sup>N²/4</sup>:</p>");
        h.append("<ul>");
        h.append("<li>4×4 — 256 вариантов (мало, вскрывается перебором вручную)</li>");
        h.append("<li>6×6 — 262 144</li>");
        h.append("<li>8×8 — 4 294 967 296</li>");
        h.append("<li>10×10 — около 1,1·10<sup>15</sup></li>");
        h.append("<li>12×12 — около 4,7·10<sup>21</sup></li>");
        h.append("</ul>");

        h.append("<h3 style='font-size:15px'>Насколько это стойко</h3>");
        h.append("<p>По современным меркам — нисколько, и вот почему. Шифр только переставляет буквы, поэтому ");
        h.append("частоты букв в шифртексте те же, что и в обычном тексте: видно, что это перестановка, а не замена. ");
        h.append("Все блоки шифруются одной и той же решёткой, значит буква на позиции i каждого блока всегда ");
        h.append("приходит с одной и той же позиции — имея несколько блоков или пару «текст + шифртекст», ключ ");
        h.append("восстанавливают почти сразу. Для больших N перебор велик, но криптоанализ идёт не перебором, ");
        h.append("а сборкой осмысленных кусков текста (метод анаграмм). Это учебный и исторический шифр — ");
        h.append("для реальной защиты данных берут AES и подобные.</p>");

        h.append("<h3 style='font-size:15px'>Мелочи этой программы</h3>");
        h.append("<ul>");
        h.append("<li>Текст не «чистится»: пробелы, знаки препинания, регистр и цифры сохраняются как есть. ");
        h.append("Классический вариант шифра работал с текстом без пробелов и в одном регистре — так надёжнее, ");
        h.append("потому что пробелы подсказывают криптоаналитику границы слов.</li>");
        h.append("<li>Символ-заполнитель (по умолчанию «·») добивает последний блок и при расшифровке ");
        h.append("отбрасывается с конца. Поэтому если сам текст заканчивается на этот символ, он тоже пропадёт — ");
        h.append("выберите заполнителем то, чего в тексте точно нет.</li>");
        h.append("<li>Вкладка «Как это работает» показывает все четыре шага на вашем собственном тексте.</li>");
        h.append("</ul>");
        h.append("</body></html>");

        JEditorPane pane = new JEditorPane("text/html", h.toString());
        pane.setEditable(false);
        pane.setBackground(Color.WHITE);
        pane.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(pane);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    /* ---------- действия ---------- */

    private void doEncrypt() {
        String text = inputArea.getText();
        if (text.isEmpty()) {
            error("Введите текст, который нужно зашифровать");
            return;
        }
        String res = GrilleCipher.encrypt(text, grille, filler());
        outputArea.setText(res);
        int blocks = res.length() / (grille.size * grille.size);
        say("Зашифровано: " + text.length() + " симв. → " + res.length() + " симв. ("
                + blocks + " блок(ов) по " + (grille.size * grille.size) + "). Ключ: " + grille.toKey());
        demoPanel.setData(grille, text, filler());
    }

    private void doDecrypt() {
        String text = inputArea.getText();
        if (text.isEmpty()) {
            error("Вставьте в верхнее поле шифртекст");
            return;
        }
        try {
            String res = GrilleCipher.decrypt(text, grille, filler());
            outputArea.setText(res);
            say("Расшифровано: " + text.length() + " симв. → " + res.length() + " симв. Ключ: " + grille.toKey());
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
        }
    }

    private void setGrille(Grille g, String message) {
        this.grille = g;
        grillePanel.setGrille(g);
        keyField.setText(g.toKey());
        say(message + ". Ключ: " + g.toKey() + (g.isValid() ? "" : "  [ВНИМАНИЕ: решётка некорректна]"));
        revalidate();
        repaint();
    }

    private char filler() {
        String s = fillerField.getText();
        return s.isEmpty() ? '·' : s.charAt(0);
    }

    private void say(String s) {
        status.setText(s);
    }

    private void error(String s) {
        status.setText(s);
        JOptionPane.showMessageDialog(this, s, "Не получилось", JOptionPane.WARNING_MESSAGE);
    }

    private void toClipboard(String s) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
    }
}
