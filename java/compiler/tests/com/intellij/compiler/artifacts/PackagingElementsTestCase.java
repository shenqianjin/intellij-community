package com.intellij.compiler.artifacts;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.util.PathUtil;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public abstract class PackagingElementsTestCase extends ArtifactsTestCase {
  protected Artifact addArtifact(PackagingElementBuilder builder) {
    return addArtifact("a", builder);
  }

  protected Artifact addArtifact(final String name, PackagingElementBuilder builder) {
    return addArtifact(name, builder.build());
  }

  protected void assertLayout(Artifact artifact, String expected) {
    assertLayout(artifact.getRootElement(), expected);
  }

  protected void assertLayout(PackagingElement element, String expected) {
    ArtifactsTestUtil.assertLayout(element, expected);
  }

  protected String getProjectBasePath() {
    return getBaseDir().getPath();
  }

  protected VirtualFile getBaseDir() {
    final VirtualFile baseDir = myProject.getBaseDir();
    assertNotNull(baseDir);
    return baseDir;
  }

  protected static PackagingElementFactory getFactory() {
    return PackagingElementFactory.getInstance();
  }

  protected PackagingElementBuilder root() {
    return new PackagingElementBuilder(getFactory().createArtifactRootElement(), null);
  }

  protected PackagingElementBuilder archive(String name) {
    return new PackagingElementBuilder(getFactory().createArchive(name), null);
  }

  protected PackagingElementBuilder dir(String name) {
    return new PackagingElementBuilder(getFactory().createDirectory(name), null);
  }

  protected VirtualFile createFile(final String path) throws IOException {
    return createFile(path, "");
  }

  protected VirtualFile createFile(final String path, final String text) throws IOException {
    return new WriteAction<VirtualFile>() {
      protected void run(final Result<VirtualFile> result) {
        try {
          VirtualFile parent = getBaseDir();
          assertNotNull(parent);
          StringTokenizer parents = new StringTokenizer(PathUtil.getParentPath(path), "/");
          while (parents.hasMoreTokens()) {
            final String name = parents.nextToken();
            VirtualFile child = parent.findChild(name);
            if (child == null) {
              child = parent.createChildDirectory(this, name);
            }
            parent = child;
          }
          final VirtualFile file = parent.createChildData(this, PathUtil.getFileName(path));
          VfsUtil.saveText(file, text);
          result.setResult(file);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      }
    }.execute().getResultObject();
  }

  protected VirtualFile getJDomJar() {
    return getJarFromLibDirectory("jdom.jar");
  }

  protected String getJUnitJarPath() {
    final VirtualFile jar = getJarFromLibDirectory("junit.jar");
    return JarFileSystem.getInstance().getVirtualFileForJar(jar).getPath();
  }

  private VirtualFile getJarFromLibDirectory(final String relativePath) {
    final File file = PathManager.findFileInLibDirectory(relativePath);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    assertNotNull(file.getAbsolutePath() + " not found", virtualFile);
    final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
    assertNotNull(jarRoot);
    return jarRoot;
  }

  protected Library addProjectLibrary(final @Nullable Module module, final String name, final VirtualFile... jars) {
    return new WriteAction<Library>() {
      protected void run(final Result<Library> result) {
        final Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).createLibrary(name);
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        for (VirtualFile jar : jars) {
          libraryModel.addRoot(jar, OrderRootType.CLASSES);
        }
        libraryModel.commit();
        if (module != null) {
          final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
          rootModel.addLibraryEntry(library);
          rootModel.commit();
        }
        result.setResult(library);
      }
    }.execute().getResultObject();
  }

  protected Library addModuleLibrary(final Module module, final VirtualFile... jars) {
    return new WriteAction<Library>() {
      protected void run(final Result<Library> result) {
        final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        final Library library = rootModel.getModuleLibraryTable().createLibrary();
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        for (VirtualFile jar : jars) {
          libraryModel.addRoot(jar, OrderRootType.CLASSES);
        }
        libraryModel.commit();
        rootModel.commit();
        result.setResult(library);
      }
    }.execute().getResultObject();
  }

  protected void addModuleDependency(final Module module, final Module dependency) {
    new WriteAction() {
      protected void run(final Result result) {
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        model.addModuleOrderEntry(dependency);
        model.commit();
      }
    }.execute();
  }

  protected class PackagingElementBuilder {
    private final CompositePackagingElement<?> myElement;
    private final PackagingElementBuilder myParent;

    private PackagingElementBuilder(CompositePackagingElement<?> element, PackagingElementBuilder parent) {
      myElement = element;
      myParent = parent;
    }

    public CompositePackagingElement<?> build() {
      PackagingElementBuilder builder = this;
      while (builder.myParent != null) {
        builder = builder.myParent;
      }
      return builder.myElement;
    }

    public PackagingElementBuilder file(VirtualFile file) {
      return file(file.getPath());
    }

    public PackagingElementBuilder file(String path) {
      myElement.addOrFindChild(getFactory().createFileCopyWithParentDirectories(path, "/"));
      return this;
    }

    public PackagingElementBuilder dirCopy(VirtualFile dir) {
      return dirCopy(dir.getPath());
    }

    public PackagingElementBuilder dirCopy(String path) {
      myElement.addOrFindChild(getFactory().createDirectoryCopyWithParentDirectories(path, "/"));
      return this;
    }

    public PackagingElementBuilder extractedDir(String jarPath, String pathInJar) {
      myElement.addOrFindChild(getFactory().createExtractedDirectoryWithParentDirectories(jarPath, pathInJar, "/"));
      return this;
    }

    public PackagingElementBuilder module(Module module) {
      myElement.addOrFindChild(getFactory().createModuleOutput(module));
      return this;
    }

    public PackagingElementBuilder lib(Library library) {
      myElement.addOrFindChildren(getFactory().createLibraryElements(library));
      return this;
    }

    public PackagingElementBuilder artifact(Artifact artifact) {
      myElement.addOrFindChild(getFactory().createArtifactElement(artifact, myProject));
      return this;
    }

    public PackagingElementBuilder archive(String name) {
      final CompositePackagingElement<?> archive = getFactory().createArchive(name);
      return new PackagingElementBuilder(myElement.addOrFindChild(archive), this);
    }

    public PackagingElementBuilder dir(String name) {
      return new PackagingElementBuilder(myElement.addOrFindChild(getFactory().createDirectory(name)), this);
    }

    public PackagingElementBuilder add(PackagingElement<?> element) {
      myElement.addOrFindChild(element);
      return this;
    }

    public PackagingElementBuilder end() {
      return myParent;
    }
  }
}
