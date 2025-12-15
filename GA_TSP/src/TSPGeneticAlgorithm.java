import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class TSPGeneticAlgorithm {

    // --- PARÂMETROS (AG PADRÃO - SEM ENTROPIA) ---
    static final int POPULATION_SIZE = 100;
    static final int MAX_GENERATIONS = 1000;
    static final int MAX_STAGNATION = 100;
    static final double CROSSOVER_RATE = 0.9;
    static final double MUTATION_RATE = 0.02;
    static final boolean ELITISM = true;
    
    // --- ESTRUTURAS DE DADOS ---
    static class City {
        int id;
        double x, y;

        public City(int id, double x, double y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }

        public double distanceTo(City c) {
            double xDist = this.x - c.x;
            double yDist = this.y - c.y;
            return Math.sqrt((xDist * xDist) + (yDist * yDist));
        }
    }

    static class Individual implements Comparable<Individual> {
        List<City> genome;
        double distance = 0;
        double fitness = 0;

        public Individual() {
            genome = new ArrayList<>();
        }

        // Construtor de cópia para evitar erro de referência
        public Individual(List<City> genome) {
            this.genome = new ArrayList<>(genome);
            calculateDistanceAndFitness();
        }

        public void calculateDistanceAndFitness() {
            distance = 0;
            for (int i = 0; i < genome.size(); i++) {
                City from = genome.get(i);
                City to = genome.get((i + 1) % genome.size());
                distance += from.distanceTo(to);
            }
            fitness = 1.0 / distance;
        }

        @Override
        public int compareTo(Individual other) {
            return Double.compare(other.fitness, this.fitness);
        }
    }

    static List<City> allCities = new ArrayList<>();

    public static void main(String[] args) {
        // Tenta carregar o arquivo. Se der erro, verifique se kroA100.tsp está na pasta do projeto.
        loadTSPFile("kroA100.tsp");
        
        if (allCities.isEmpty()) {
            System.out.println("Erro: Nenhuma cidade carregada. Verifique o arquivo kroA100.tsp");
            return;
        }
        
        System.out.println("AG Padrão Iniciado (Correção de Referência Aplicada)...");

        List<Individual> population = initializePopulation();
        Collections.sort(population); 
        
        // CORREÇÃO: Cria uma cópia profunda do genoma, não usa referência direta
        Individual bestGlobal = new Individual(population.get(0).genome);
        int generationsWithoutImprovement = 0;

        for (int generation = 1; generation <= MAX_GENERATIONS; generation++) {
            List<Individual> newPopulation = new ArrayList<>();

            if (ELITISM) {
                // Elitismo: Adiciona cópia segura do melhor da geração anterior
                newPopulation.add(new Individual(population.get(0).genome));
            }

            while (newPopulation.size() < POPULATION_SIZE) {
                Individual p1 = rouletteSelection(population);
                Individual p2 = rouletteSelection(population);

                Individual child;
                if (Math.random() < CROSSOVER_RATE) {
                    child = orderCrossover(p1, p2);
                } else {
                    // Se não cruzar, clona o pai (IMPORTANTE: new Individual)
                    child = new Individual(p1.genome); 
                }

                mutate(child);
                child.calculateDistanceAndFitness(); // Recalcula após mutação
                newPopulation.add(child);
            }

            population = newPopulation;
            Collections.sort(population);

            Individual currentBest = population.get(0);

            // Atualiza o melhor global se encontrou um melhor
            if (currentBest.fitness > bestGlobal.fitness) {
                bestGlobal = new Individual(currentBest.genome); // Salva cópia
                generationsWithoutImprovement = 0;
                System.out.printf("Gen %d: Novo Melhor Custo = %.2f\n", generation, bestGlobal.distance);
            } else {
                generationsWithoutImprovement++;
            }

            if (generationsWithoutImprovement >= MAX_STAGNATION) {
                System.out.println("Parada por estagnação na geração " + generation);
                break;
            }
        }

        System.out.println("------------------------------------------------");
        System.out.printf("Melhor Custo Final (AG Padrão): %.2f\n", bestGlobal.distance);
        System.out.println("------------------------------------------------");

        drawRoute(bestGlobal);
    }

    private static List<Individual> initializePopulation() {
        List<Individual> pop = new ArrayList<>();
        int sizeNN = (int) (POPULATION_SIZE * 0.20);
        int sizeRand = POPULATION_SIZE - sizeNN;

        for (int i = 0; i < sizeRand; i++) {
            List<City> genome = new ArrayList<>(allCities);
            Collections.shuffle(genome);
            pop.add(new Individual(genome));
        }

        for (int i = 0; i < sizeNN; i++) {
            pop.add(generateNearestNeighbor());
        }

        return pop;
    }

    private static Individual generateNearestNeighbor() {
        List<City> unvisited = new ArrayList<>(allCities);
        List<City> path = new ArrayList<>();
        Random rand = new Random();
        City current = unvisited.remove(rand.nextInt(unvisited.size()));
        path.add(current);

        while (!unvisited.isEmpty()) {
            City nearest = null;
            double minDist = Double.MAX_VALUE;
            for (City c : unvisited) {
                double d = current.distanceTo(c);
                if (d < minDist) {
                    minDist = d;
                    nearest = c;
                }
            }
            current = nearest;
            unvisited.remove(nearest);
            path.add(current);
        }

        return new Individual(path);
    }

    private static Individual rouletteSelection(List<Individual> population) {
        double totalFitness = 0;
        for (Individual ind : population) totalFitness += ind.fitness;
        double value = Math.random() * totalFitness;
        double currentSum = 0;
        for (Individual ind : population) {
            currentSum += ind.fitness;
            if (currentSum >= value) return ind;
        }
        return population.get(population.size() - 1);
    }

    private static Individual orderCrossover(Individual p1, Individual p2) {
        int size = p1.genome.size();
        Random rand = new Random();
        int start = rand.nextInt(size);
        int end = rand.nextInt(size);
        if (start > end) { int temp = start; start = end; end = temp; }

        City[] childGenome = new City[size];
        Set<Integer> containedCities = new HashSet<>();

        for (int i = start; i <= end; i++) {
            childGenome[i] = p1.genome.get(i);
            containedCities.add(p1.genome.get(i).id);
        }

        int p2Index = 0;
        for (int i = 0; i < size; i++) {
            if (i >= start && i <= end) continue;
            while (p2Index < size && containedCities.contains(p2.genome.get(p2Index).id)) {
                p2Index++;
            }
            if (p2Index < size) {
                childGenome[i] = p2.genome.get(p2Index);
                containedCities.add(p2.genome.get(p2Index).id);
            }
        }
        return new Individual(Arrays.asList(childGenome));
    }

    private static void mutate(Individual ind) {
        if (Math.random() < MUTATION_RATE) {
            Random rand = new Random();
            int i = rand.nextInt(ind.genome.size());
            int j = rand.nextInt(ind.genome.size());
            Collections.swap(ind.genome, i, j);
        }
    }

    private static void loadTSPFile(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean coordSection = false;
            while ((line = br.readLine()) != null) {
                if (line.contains("NODE_COORD_SECTION")) { coordSection = true; continue; }
                if (line.contains("EOF")) break;
                if (coordSection) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3) {
                        try {
                            allCities.add(new City(Integer.parseInt(parts[0]), 
                                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
                        } catch (Exception e) {}
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void drawRoute(Individual best) {
        JFrame frame = new JFrame("Melhor Rota AG Padrão - Custo: " + String.format("%.2f", best.distance));
        frame.setSize(800, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(); int h = getHeight(); int margin = 50;
                double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE, minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
                for (City c : best.genome) {
                    if (c.x < minX) minX = c.x; if (c.x > maxX) maxX = c.x;
                    if (c.y < minY) minY = c.y; if (c.y > maxY) maxY = c.y;
                }
                double scaleX = (w - 2 * margin) / (maxX - minX);
                double scaleY = (h - 2 * margin) / (maxY - minY);
                g2.setColor(Color.BLUE);
                for (int i = 0; i < best.genome.size(); i++) {
                    City c1 = best.genome.get(i);
                    City c2 = best.genome.get((i + 1) % best.genome.size());
                    int x1 = margin + (int) ((c1.x - minX) * scaleX);
                    int y1 = h - margin - (int) ((c1.y - minY) * scaleY);
                    int x2 = margin + (int) ((c2.x - minX) * scaleX);
                    int y2 = h - margin - (int) ((c2.y - minY) * scaleY);
                    g2.drawLine(x1, y1, x2, y2);
                }
                g2.setColor(Color.RED);
                for (City c : best.genome) {
                    int x = margin + (int) ((c.x - minX) * scaleX);
                    int y = h - margin - (int) ((c.y - minY) * scaleY);
                    g2.fillOval(x - 3, y - 3, 6, 6);
                }
            }
        };
        frame.add(panel);
        frame.setVisible(true);
    }
}