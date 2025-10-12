import java.io.IOException;

public class ProgressBar {

    public static void main(String[] args) throws IOException, InterruptedException {

        int totalItems = 50;
        int barLength = 50;

        System.out.println("Processing items...");

        for (int i = 1; i <= totalItems; i++) {
            // Calculate progress percentage
            double progress = ((double) i / totalItems) * 100;
            
            // Calculate the number of '#' characters for the bar
            int filledLength = (int) (barLength * (progress / 100));
            
            // Build the progress bar string
            StringBuilder bar = new StringBuilder();
            for (int j = 0; j < filledLength; j++) {
                bar.append("#");
            }
            for (int j = filledLength; j < barLength; j++) {
                bar.append("-");
            }

            // The core of the dynamic progress bar:
            // 1. "\r" (Carriage Return) moves the cursor to the start of the line.
            // 2. System.out.print() writes the new string without adding a newline.
            //    This overwrites the previous line.
            System.out.print("\r[" + bar + "] " + String.format("%.2f", progress) + "% (" + i + "/" + totalItems + ")");

            // 3. System.out.flush() forces the output to be written immediately,
            //    making the animation visible.
            System.out.flush();

            // Simulate some work being done
            Thread.sleep(100);
        }

        // Add a final newline character to ensure the next output starts on a new line
        System.out.println();
        System.out.println("Processing complete.");
    }
}