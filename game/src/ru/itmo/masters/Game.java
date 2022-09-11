package ru.itmo.masters;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;


enum GridCellType {
    FLOOR,
    WALL,
    PLAYER,
    ENEMY,
    OUT_OF_GRID
}

record AgentPos(int col, int row) {
}

record Pair<T>(T fst, T snd) {
};


class MazeGenerator {
    static ArrayList<ArrayList<GridCellType>> genMaze(int col, int row, float mapDensity) {
        ArrayList<ArrayList<GridCellType>> maze = new ArrayList<>(row);
        for (int i = 0; i < row; i++) {
            maze.add(new ArrayList<>(col));
        }

        Random randGen = new Random();
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < col; j++) {
                maze.get(i).add(randGen.nextFloat() < mapDensity ? GridCellType.WALL : GridCellType.FLOOR);
            }
        }

        return maze;
    }
}


class Game {
    private Terminal term;
    private final TerminalSize termSz;
    private int FPS;
    private int EPS;

    private final float mapDensity;

    private final int nEnemies;

    private final Path gameGridSerializationPath = Path.of("resources",
            "grids",
            "serializedGameGrid");


    private ArrayList<ArrayList<GridCellType>> gameGrid;

    private AgentPos playerPos;
    private TextColor playerColor = TextColor.ANSI.MAGENTA;
    private ArrayList<AgentPos> enemyPositions = new ArrayList<>();
    private final TextColor enemyColor = TextColor.ANSI.BLUE;

    public Game(int col, int row, int someFPS, int someEPS, int someNumEnemies, float someMapDensity) {
        termSz = new TerminalSize(col, row);
        EPS = someEPS;
        nEnemies = someNumEnemies;
        mapDensity = someMapDensity;
        try {
            initGame(someFPS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initGame(int someFps) throws IOException {
        FPS = someFps;
        term = new DefaultTerminalFactory().setInitialTerminalSize(termSz).createTerminal();
        initTerm(term);

        initGrid();
    }

    private void changePlayerColor() {
        if (playerColor == TextColor.ANSI.MAGENTA)
            playerColor = TextColor.ANSI.YELLOW;
        else
            playerColor = TextColor.ANSI.MAGENTA;
    }

    private void initGrid() {
        gameGrid = MazeGenerator.genMaze(termSz.getColumns(), termSz.getRows(), mapDensity);
        spawnPlayer();
        spawnEnemies(nEnemies);
    }

    private void spawnPlayer() {
        playerPos = spawnAgent(termSz.getColumns(), termSz.getRows(), GridCellType.PLAYER);
    }

    private void spawnEnemies(int n) {
        enemyPositions.clear();
        for (int i = 0; i < n; i++)
            enemyPositions.add(spawnAgent(termSz.getColumns(), termSz.getRows(), GridCellType.ENEMY));
    }

    private ArrayList<AgentPos> findAgents(GridCellType gridCellType) {
        ArrayList<AgentPos> agentsFound = new ArrayList<>();
        if (gridCellType != GridCellType.PLAYER && gridCellType != GridCellType.ENEMY)
            throw new IllegalArgumentException(gridCellType.toString() + " is not an agent cell type");
        for (int i = 0; i < gameGrid.size(); i++) {
            for (int j = 0; j < gameGrid.get(0).size(); j++) {
                if (gameGrid.get(i).get(j) == gridCellType)
                    agentsFound.add(new AgentPos(j, i));
            }
        }
        if (agentsFound.isEmpty())
            throw new IllegalStateException(gridCellType + " not found on game grid");
        return agentsFound;
    }

    private void initTerm(Terminal someTerm) throws IOException {
        someTerm.enterPrivateMode();
        someTerm.clearScreen();
        someTerm.setCursorVisible(false);
    }

    private AgentPos spawnAgent(int maxCol, int maxRow, GridCellType agentType) {
        Random randGen = new Random();
        while (true) {
            int randCol = randGen.nextInt(0, maxCol);
            int randRow = randGen.nextInt(0, maxRow);
            if (gameGrid.get(randRow).get(randCol) == GridCellType.FLOOR) {
                gameGrid.get(randRow).set(randCol, agentType);
                return new AgentPos(randCol, randRow);
            }
        }
    }

    private char cellTypeToFace(GridCellType cellType) {
        switch (cellType) {
            case FLOOR -> {
                return ' ';
            }
            case WALL -> {
                return '#';
            }
            case PLAYER -> {
                return '@';
            }
            case ENEMY -> {
                return '&';
            }
            default -> throw new IllegalStateException("Unknown GridCellType: " + cellType.name());
        }
    }

    private void drawCellFace(int i, int j) throws IOException {
        if (gameGrid.get(i).get(j) == GridCellType.PLAYER) {
            term.enableSGR(SGR.BOLD);
            term.setForegroundColor(playerColor);
        }

        if (gameGrid.get(i).get(j) == GridCellType.ENEMY) {
            term.enableSGR(SGR.BOLD);
            term.setForegroundColor(enemyColor);
        }
        term.putCharacter(
                cellTypeToFace(gameGrid.get(i).get(j))
        );
        term.resetColorAndSGR();
    }

    private void render() throws IOException {

        for (int i = 0; i < gameGrid.size(); i++) {
            for (int j = 0; j < gameGrid.get(0).size(); j++) {
                term.setCursorPosition(j, i);
                drawCellFace(i, j);
            }
        }
        term.flush();
    }

    private GridCellType willStepOn(AgentPos agentPos, int dCol, int dRow) {
        int agCol = agentPos.col();
        int agRow = agentPos.row();

        if (
                agCol + dCol >= termSz.getColumns() ||
                        agRow + dRow >= termSz.getRows() ||
                        agCol + dCol < 0 ||
                        agRow + dRow < 0
        )
            return GridCellType.OUT_OF_GRID;
        else {
            return gameGrid.get(agRow + dRow).get(agCol + dCol);
        }
    }

    private AgentPos moveSafelyBy(AgentPos agentPos, int dCol, int dRow) {
        int agCol = agentPos.col();
        int agRow = agentPos.row();

        gameGrid.get(agRow + dRow).set(agCol + dCol, gameGrid.get(agRow).get(agCol));
        gameGrid.get(agRow).set(agCol, GridCellType.FLOOR);

        return new AgentPos(agCol + dCol, agRow + dRow);
    }

    private void respondKeyStroke(@NotNull KeyStroke keyStroke) throws IOException {

        if (keyStroke.isShiftDown() && keyStroke.getKeyType() == KeyType.Character) {
            switch (keyStroke.getCharacter()) {
                case 'H' -> showHelpMsg(false);
                case 'S' -> {
                    saveMap();
                    showInfo("Map Saved", 1000);
                }
                case 'R' -> {
                    regenerateMap();
                    showInfo("Map Regenerated", 1000);
                }

                case 'L' -> {
                    try {
                        loadPrevMapIfExists();
                        showInfo("Map loaded", 1000);
                    } catch (FileNotFoundException fne) {
                        showInfo("Can't load map (not found)", 1000);
                    } catch (IllegalArgumentException iae) {
                        showInfo("Can't load map (size mismatch)", 1000);
                    }
                }
            }
        } else if (keyStroke.getKeyType() == KeyType.Character) {
            switch (keyStroke.getCharacter()) {
                case 'h' -> {
                    if (willStepOn(playerPos, -1, 0) == GridCellType.FLOOR) {
                        playerPos = moveSafelyBy(playerPos, -1, 0);
                    }
                }
                case 'j' -> {
                    if (willStepOn(playerPos, 0, 1) == GridCellType.FLOOR) {
                        playerPos = moveSafelyBy(playerPos, 0, 1);
                    }
                }
                case 'k' -> {
                    if (willStepOn(playerPos, 0, -1) == GridCellType.FLOOR) {
                        playerPos = moveSafelyBy(playerPos, 0, -1);
                    }
                }
                case 'l' -> {
                    if (willStepOn(playerPos, 1, 0) == GridCellType.FLOOR) {
                        playerPos = moveSafelyBy(playerPos, 1, 0);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void showInfo(String msg, long durationMs) throws IOException {
        String fullMsg = "Info: " + msg;
        TextGraphics textGraphics = term.newTextGraphics();
        textGraphics.setForegroundColor(TextColor.ANSI.YELLOW);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.putString(2, 1, fullMsg, SGR.BOLD);
        term.flush();

        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException ignored) {
        }
    }

    private void loadPrevMapIfExists() throws IOException, IllegalArgumentException {
        try (FileInputStream fis = new FileInputStream(String.valueOf(gameGridSerializationPath));
             ObjectInputStream ois = new ObjectInputStream(fis)) {

            ArrayList<ArrayList<GridCellType>> loadedGameGrid = (ArrayList<ArrayList<GridCellType>>) ois.readObject();
            if (loadedGameGrid.size() != gameGrid.size() || loadedGameGrid.get(0).size() != loadedGameGrid.get(0).size())
                throw new IllegalArgumentException();
            gameGrid = loadedGameGrid;
            playerPos = findAgents(GridCellType.PLAYER).get(0);
            enemyPositions = findAgents(GridCellType.ENEMY);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void regenerateMap() throws IOException {
        initGrid();
        render();
    }

    private void saveMap() throws IOException {

        try (FileOutputStream fos = new FileOutputStream(String.valueOf(gameGridSerializationPath));
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(gameGrid);
        }
    }

    private boolean isShowHelp(@NotNull KeyStroke keyStroke) {
        return keyStroke.getKeyType() == KeyType.Character
                && keyStroke.isShiftDown()
                && keyStroke.getCharacter() == 'H';
    }

    private void showHelpMsg(boolean onStart) throws IOException {
        TextGraphics textGraphics = term.newTextGraphics();
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);

        textGraphics.setForegroundColor(TextColor.ANSI.YELLOW);
        textGraphics.putString(2, 1, "This is a Help Message", SGR.BOLD);

        textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
        textGraphics.putString(5, 2, "About: ", SGR.BOLD);

        textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
        textGraphics.putString(10, 3, "Playing for `@` try to escape from `&`.");


        textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
        textGraphics.putString(5, 4, "Use following keys to control an application: ", SGR.BOLD);

        textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
        textGraphics.putString(10, 5, " h,j,k,l  : movement");
        textGraphics.putString(10, 6, "<shift+h>: show/hide this Help Message");
        textGraphics.putString(10, 7, "<shift+r>: regenerate map");
        textGraphics.putString(10, 8, "<shift+s>: save current game state");
        textGraphics.putString(10, 9, "<shift+l>: load previously saved game state (if present)");
        if (onStart) {
            textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
            textGraphics.putString(5, 10, "Hide this message (<shift+h>) to start the game.", SGR.BOLD);
        }
        term.flush();
        KeyStroke keyStroke = term.pollInput();

        while (true) {
            if (keyStroke != null && isShowHelp(keyStroke))
                return;
            keyStroke = term.pollInput();
        }


    }

    private void evolveGrid() throws IOException {
        Random randGen = new Random();
        for (int i = 0; i < enemyPositions.size(); i++)
            evolveEnemyAggressive(randGen.nextFloat() / 5 + 0.1f, i);

    }

    private void evolveEnemyAggressive(float anger, int enemyId) throws IOException {

        Random randGen = new Random();

        Pair<Integer> randStep;
        if (randGen.nextFloat() < anger) {
            if (randGen.nextBoolean())
                randStep = new Pair<>(
                        Math.round(Math.signum(playerPos.col() - enemyPositions.get(enemyId).col())), 0
                );
            else
                randStep = new Pair<>(
                        0, Math.round(Math.signum(playerPos.row() - enemyPositions.get(enemyId).row()))
                );
        } else {
            if (randGen.nextBoolean())
                randStep = new Pair<>(randGen.nextBoolean() ? -1 : 1, 0);
            else
                randStep = new Pair<>(0, randGen.nextBoolean() ? -1 : 1);
        }

        GridCellType nextStep = willStepOn(enemyPositions.get(enemyId), randStep.fst(), randStep.snd());
        if (nextStep == GridCellType.FLOOR)
            enemyPositions.set(enemyId, moveSafelyBy(enemyPositions.get(enemyId), randStep.fst(), randStep.snd()));
        else if (nextStep == GridCellType.PLAYER) {
            changePlayerColor();
            showInfo("Gotcha!", 200);
        }

    }

    public void run() throws IOException {

        long prevRenderTimeMs = System.currentTimeMillis();
        long prevWorldEvolveMs = System.currentTimeMillis();
        render();
        showHelpMsg(true);

        KeyStroke keyStroke = term.pollInput();

        while (keyStroke == null || keyStroke.getKeyType() != KeyType.Escape) {

            if (keyStroke != null)
                respondKeyStroke(keyStroke);

            if (System.currentTimeMillis() - prevWorldEvolveMs > 1000. / EPS) {
                evolveGrid();
                prevWorldEvolveMs = System.currentTimeMillis();
            }

            if (System.currentTimeMillis() - prevRenderTimeMs > 1000. / FPS) {
                render();
                prevRenderTimeMs = System.currentTimeMillis();
            }
            keyStroke = term.pollInput();
        }
        term.close();
    }
}

