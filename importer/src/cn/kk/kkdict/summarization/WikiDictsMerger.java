package cn.kk.kkdict.summarization;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cn.kk.kkdict.extraction.dict.WikiPagesMetaCurrentExtractor;
import cn.kk.kkdict.tools.DictFilesExtractor;
import cn.kk.kkdict.tools.DictFilesMerger;
import cn.kk.kkdict.tools.DictFilesSorter;
import cn.kk.kkdict.types.Language;
import cn.kk.kkdict.types.LanguageConstants;
import cn.kk.kkdict.utils.DictHelper;
import cn.kk.kkdict.utils.Helper;

/**
 * TODO
 * 
 * @author x_kez
 * 
 */
public class WikiDictsMerger {
    private static final String OUTPUT_DICT_NAME = "output-dict.";
    private static final String SKIPPED_EXTRACTOR_NAME = "output-dict" + DictFilesExtractor.SUFFIX_SKIPPED + ".";
    public static final String IN_DIR = WikiPagesMetaCurrentExtractor.OUT_DIR;
    public static final String OUT_DIR = WikiPagesMetaCurrentExtractor.OUT_DIR;
    public static final String WORK_DIR = OUT_DIR + "\\work";
    public static final String OUT_FILE = OUT_DIR + "\\output-dict-merged.wiki";
    private static final String SKIPPED_MERGED_NAME = "output-dict" + DictFilesMerger.SUFFIX_SKIPPED + ".";

    /**
     * @param args
     * @throws IOException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        File directory = new File(IN_DIR);
        File workDirFile = new File(WORK_DIR);
        if (workDirFile.isDirectory() || !workDirFile.isFile()) {
            if (workDirFile.isDirectory()) {
                System.out.println("临时文件夹已存在：'" + WORK_DIR + "'。删除临时文件夹...");
                Helper.deleteDirectory(workDirFile);
            }
            new File(OUT_DIR).mkdirs();
            new File(WORK_DIR).mkdirs();
            System.out.print("搜索wiki词典文件'" + IN_DIR + "' ... ");

            File[] files = directory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(OUTPUT_DICT_NAME);
                }
            });
            System.out.println(files.length);

            String[] filePaths = Helper.getFileNames(files);
            int step = 0;
            System.out.println("\n【" + (++step) + "。导出所有含有中文词组的数据 】");
            DictFilesExtractor extractor = new DictFilesExtractor(Language.ZH, WORK_DIR, DictFilesExtractor.OUTFILE,
                    true, filePaths);
            extractor.extract();
            System.out.println("【" + step + "：输出文件：'" + extractor.outFile + "'】");
            String extractorOutFile = extractor.outFile;
            extractor = null;

            // rename to original
            files = workDirFile.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(SKIPPED_EXTRACTOR_NAME);
                }
            });
            for (File f : files) {
                if (f.length() > DictHelper.SEP_LIST_BYTES.length) {
                    f.renameTo(new File(f.getAbsolutePath().replace(SKIPPED_EXTRACTOR_NAME, OUTPUT_DICT_NAME)));
                } else {
                    f.delete();
                    System.out.println("删除空白文件：" + f.getAbsolutePath());
                }
            }

            List<String> tasks = new LinkedList<String>();
            for (Language wiki : DictHelper.TOP_LANGUAGES) {
                tasks.add(wiki.key);
            }
            for (String wiki : LanguageConstants.KEYS_WIKI) {
                if (!tasks.contains(wiki)) {
                    tasks.add(wiki);
                }
            }
            tasks.remove(Language.ZH.key);
            File mf = new File(extractorOutFile.substring(0, extractorOutFile.lastIndexOf(File.separatorChar))
                    + File.separator + "output-dict_main.wiki");
            new File(extractorOutFile).renameTo(mf);
            String mainFile = mf.getAbsolutePath();

            for (int i = 1; i <= 3; i++) {
                System.out.println("\n【" + (++step) + "。合并数据 " + i + "】");
                long start = System.currentTimeMillis();
                merge(step, workDirFile, tasks, mainFile);
                System.out.println("【" + step + "：合并数据 " + i + "完成，合并文件大小："
                        + Helper.formatSpace(new File(mainFile).length()) + "，用时："
                        + Helper.formatDuration(System.currentTimeMillis() - start) + "】");
            }

            new File(mainFile).renameTo(new File(OUT_FILE));
            Helper.deleteDirectory(workDirFile);

            System.out.println("\n=====================================");
            System.out.println("总共读取词典文件：" + files.length);
            System.out.println("=====================================");
        } else {
            System.err.println("临时文件夹已被占用：'" + WORK_DIR + "'!");
        }
    }

    private static void merge(int step, File workDirFile, List<String> tasks, String mainFile) throws IOException,
            InterruptedException {
        TimeUnit.SECONDS.sleep(1);

        // 1. 搜寻文件名
        File[] files = workDirFile.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(OUTPUT_DICT_NAME);
            }
        });
        String[] filePaths = Helper.getFileNames(files);
        for (String task : tasks) {
            int step2 = 0;
            Language lng = Language.valueOf(task.toUpperCase());
            System.out.println("\n。。。【" + step + "。合并语言'" + task + "'】");

            System.out.println("。。。【" + step + "。" + (++step2) + "。导出所有含有'" + task + "'词组的数据 】");
            String lngOutFile = Helper.appendFileName(DictFilesExtractor.OUTFILE, "_lng");
            DictFilesExtractor extractor = new DictFilesExtractor(lng, WORK_DIR, lngOutFile, false, filePaths);
            extractor.extract();
            lngOutFile = extractor.outFile;
            System.out.println("。。。【" + step + "。" + step2 + "：输出文件：'" + lngOutFile + "'】");
            if (new File(lngOutFile).length() > DictHelper.SEP_LIST_BYTES.length) {
                System.out.println("。。。【" + step + "。" + (++step2) + "。排序文件：'" + mainFile + "'，语言：'" + task + "'】");
                DictFilesSorter sorter = new DictFilesSorter(lng, WORK_DIR, false, mainFile);
                sorter.sort();
                String mainSortedFile = sorter.outFile;
                new File(mainFile).delete();
                new File(mainSortedFile).renameTo(new File(mainFile));
                TimeUnit.SECONDS.sleep(1);
                System.out.println("。。。【" + step + "。" + step2 + "：输出文件：'" + mainFile + "'，排序后词语数目："
                        + sorter.getTotalSorted() + "】");

                sorter = new DictFilesSorter(lng, WORK_DIR, false, lngOutFile);
                sorter.sort();
                String lngSortedFile = sorter.outFile;
                new File(lngOutFile).delete();
                new File(lngSortedFile).renameTo(new File(lngOutFile));
                TimeUnit.SECONDS.sleep(1);
                System.out.println("。。。【" + step + "。" + step2 + "：输出文件：'" + lngOutFile + "'，排序后词语数目："
                        + sorter.getTotalSorted() + "】");

                System.out.println("。。。【" + step + "。" + step2 + "。合并'" + task + "'文件：'" + lngOutFile + "'，'"
                        + mainFile + "'，语言：'" + task + "'】");
                DictFilesMerger merger = new DictFilesMerger(lng, WORK_DIR, mainFile, lngOutFile);
                merger.merge();
                new File(lngOutFile).delete();
                new File(Helper.appendFileName(lngOutFile, DictFilesMerger.SUFFIX_SKIPPED)).delete();
                new File(mainFile).delete();
                new File(merger.outFile).renameTo(new File(mainFile));
                TimeUnit.SECONDS.sleep(1);
                System.out.println("。。。【" + step + "." + step2 + "：输出文件：'" + mainFile + "'。】");
            } else {
                new File(lngOutFile).delete();
            }
        }
    }
}
