/*
Copyright (c) 2011, Tom Van Cutsem, Vrije Universiteit Brussel
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the Vrije Universiteit Brussel nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Vrije Universiteit Brussel BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.*/

package mandelbrot;

import java.awt.Canvas;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Scanner;

/**
 * Demo of using Fork/Join parallelism to speed up the rendering of the
 * Mandelbrot fractal. The fractal is shown centered around the origin
 * of the Complex plane with x and y coordinates in the interval [-2, 2].
 *
 * @author tvcutsem
 */
public class Mandelbrot extends Canvas {

    // size of fractal in pixels (HEIGHT X HEIGHT)
    private static final int HEIGHT = 1024;
    // how long to test for orbit divergence
    private static int NUM_ITERATIONS = 50;

    private int colorscheme[];

    // 2-dimensional array of colors stored in packed ARGB format
    private int[] fractal;

    private int height;
    private Image img;
    private String msg;

    private static double posX = 0, posY = 0;
    private static double factor = 4.0;

    private static Scanner scanner = new Scanner(System.in);

    /**
     * Construct a new Mandelbrot canvas.
     * The constructor will calculate the fractal (either sequentially
     * or in parallel), then store the result in an {@link java.awt.Image}
     * for faster drawing in its {@link #paint(Graphics)} method.
     *
     * @param height      the size of the fractal (height x height pixels).
     * @param optParallel if true, render in parallel
     * @param optDrawGrid if true, render the grid of leaf task pixel areas
     */
    public Mandelbrot(int height) {
        this.colorscheme = new int[NUM_ITERATIONS + 1];
        // fill array with color palette going from Red over Green to Blue
        int scale = (255 * 2) / NUM_ITERATIONS;

        // going from Red to Green
        for (int i = 0; i < (NUM_ITERATIONS / 2); i++)
            //               Alpha=255  | Red                   | Green       | Blue=0
            colorscheme[i] = 0xFF << 24 | (255 - i * scale) << 16 | i * scale << 8;

        // going from Green to Blue
        for (int i = 0; i < (NUM_ITERATIONS / 2); i++)
            //                         Alpha=255 | Red=0 | Green              | Blue
            colorscheme[i + NUM_ITERATIONS / 2] = 0xFF000000 | (255 - i * scale) << 8 | i * scale;

        // convergence color
        colorscheme[NUM_ITERATIONS] = 0xFF0000FF; // Blue

        this.height = height;
        // fractal[x][y] = fractal[x + height*y]
        this.fractal = new int[height * height];

        long start = System.currentTimeMillis();

        // sequential calculation by the main Thread
        calcMandelBrot(0, 0, height, height);

        long end = System.currentTimeMillis();
        msg = " done in " + (end - start) + "ms.";
        this.img = getImageFromArray(fractal, height, height);
    }

    /**
     * Draws part of the mandelbrot fractal.
     * <p>
     * This method calculates the colors of pixels in the square:
     * <p>
     * (srcx, srcy)           (srcx+size, srcy)
     * +--------------------------+
     * |                          |
     * |                          |
     * |                          |
     * +--------------------------+
     * (srcx, srcy+size)      (srcx+size, srcy + size)
     */
    private void calcMandelBrot(int srcx, int srcy, int size, int height) {
        double x, y, t, cx, cy;
        int k;

        // loop over specified rectangle grid
        for (int px = srcx; px < srcx + size; px++) {
            for (int py = srcy; py < srcy + size; py++) {
                x = 0;
                y = 0;
                // convert pixels into complex coordinates between (-2, 2)
                /*
                A modificação do intervalo de valores mostrados
                na tela é possibilitada através do factor.
                As variáveis posX e posY são responsáveis pela centralização
                da janela de visualização.
                */
                cx = (px * factor) / height - (2 * factor / 4.0) + posX;
                cy = posY + (2 * factor / 4.0) - (py * factor) / height;
                // test for divergence
                for (k = 0; k < NUM_ITERATIONS; k++) {
                    t = x * x - y * y + cx;
                    y = 2 * x * y + cy;
                    x = t;
                    if (x * x + y * y > 4) break;
                }
                fractal[px + height * py] = colorscheme[k];
            }
        }
    }

    @Override
    public void paint(Graphics g) {
        // draw the fractal from the stored image
        g.drawImage(this.img, 0, 0, null);
        // draw the message text in the lower-right-hand corner
        byte[] data = this.msg.getBytes();
        g.drawBytes(
                data,
                0,
                data.length,
                getWidth() - (data.length) * 8,
                getHeight() - 20);
    }

    /**
     * Auxiliary function that converts an array of pixels into a BufferedImage.
     * This is used to be able to quickly draw the fractal onto the canvas using
     * native code, instead of us having to manually plot each pixel to the canvas.
     */
    private static Image getImageFromArray(int[] pixels, int width, int height) {
        // RGBdefault expects 0x__RRGGBB packed pixels
        ColorModel cm = DirectColorModel.getRGBdefault();
        SampleModel sampleModel = cm.createCompatibleSampleModel(width, height);
        DataBuffer db = new DataBufferInt(pixels, height, 0);
        WritableRaster raster = Raster.createWritableRaster(sampleModel, db, null);
        BufferedImage image = new BufferedImage(cm, raster, false, null);
        return image;
    }

    public static void main(String args[]) {
        Frame f = new Frame();
        Mandelbrot canvas = new Mandelbrot(HEIGHT);
        f.setSize(HEIGHT, HEIGHT);
        f.add(canvas);
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        f.setVisible(true);

        boolean run = true;

        // Início do loop que obtém os inputs do usuário
        while (run) {
            System.out.println("Selecione a posição para centralizar no eixo X: (Double)");
            posX = scanner.nextDouble();

            System.out.println("Selecione a posição para centralizar no eixo Y: (Double)");
            posY = scanner.nextDouble();

            System.out.println("Selecione o Zoom: (1 - 100% [inicial], 1.5 - 150%, 2 - 200%...)");

            /*
            O fator inicial de 4 mostra o intervalo de -2 até 2 nos eixos X e Y
            Ao diminuir este fator, um intervalo menor é apresentado, dando a sensação de zoom
            Caso o usuário insira o nível 2, por exemplo, factor terá valor 2.0.
            Assim, o intervalo de valores mostrados na tela será a metade.
            Assumindo que as posições em X e Y selecionadas sejam (0,0), 
            o intervalo de valores mostrados será de -1 até 1 em ambos os eixos
            */
            factor = 4.0 / scanner.nextDouble();

            // Aumenta o número de iterações para que o fractal fique mais detalhado conforme níveis maiores de zoom são aplicados.
            NUM_ITERATIONS = (int) (50 + 10 / factor);

            // Cria um novo canvas, que contém o fractal com o zoom aplicado
            // Renderiza o novo fractal e remove o anterior
            Mandelbrot newCanvas = new Mandelbrot(HEIGHT);
            f.add(newCanvas);
            f.remove(canvas);
            canvas = newCanvas;
            f.setVisible(true);

            // Verifica se o usuário deseja continuar com a execução do programa
            System.out.println("Continuar? (s/n)");
            scanner.nextLine();
            String answer = scanner.nextLine();

            if (!answer.trim().equalsIgnoreCase("s")) {
                run = false;
                System.exit(0);
            }
        }
    }
}