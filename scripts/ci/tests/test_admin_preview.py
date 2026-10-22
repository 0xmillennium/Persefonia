"""Own-workspace classpath guard for the conditional native admin preview."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import qualification as q
spec=importlib.util.spec_from_file_location('admin_preview',Path(q.__file__).with_name('admin-preview.py'))
preview=importlib.util.module_from_spec(spec);spec.loader.exec_module(preview)

class AdminPreviewContracts(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(dir=os.environ.get('RUNNER_TEMP'));self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.tests=self.root/'app/build/classes/java/test'
        self.jte=self.root/'app/build/generated/jte-classes';self.jte.mkdir(parents=True)
        self.main=self.root/'app/build/classes/java/main';self.main.mkdir(parents=True)
        directory=self.tests/'dev/persefonia/app/webadmin/preview';directory.mkdir(parents=True)
        for name in preview.CLASSES: (directory/name).write_bytes(bytes.fromhex('cafebabe00000041'))
        self.jar=self.root/'dependencies/spring-boot-test-4.0.0.jar';self.jar.parent.mkdir();self.jar.write_bytes(b'controlled-test-dependency')
        self.payload={'schema':1,'classpath':[str(p) for p in [self.tests,self.main,self.jte,self.jar]]}
        self.witness={'executed':True,'sourceInputs':'a'*64,'testRuntime':{'hasFilters':'false'},'testDependencies':[['dependencies/'+self.jar.name,q.file_hash(self.jar)]]}

    def validate(self):
        return preview.validate_runtime(self.payload,self.root,'a'*64,self.witness,True)

    def test_validated_runtime_contains_only_owned_classes_and_actual_tested_dependencies(self):
        self.assertEqual(self.validate(),os.pathsep.join(self.payload['classpath']))

    def test_missing_stale_filtered_foreign_modified_or_newer_runtime_fails_closed(self):
        import copy
        for mode in ['missing','foreign','stale','filtered','not-executed','dependency','newer','duplicate']:
            payload,witness=copy.deepcopy(self.payload),copy.deepcopy(self.witness)
            with self.subTest(mode=mode):
                if mode=='missing': payload['classpath'].remove(str(self.tests))
                elif mode=='foreign': payload['classpath'].append(str(self.root.parent))
                elif mode=='stale': witness['sourceInputs']='b'*64
                elif mode=='filtered': witness['testRuntime']['hasFilters']='true'
                elif mode=='not-executed': witness['executed']=False
                elif mode=='dependency': witness['testDependencies'][0][1]='0'*64
                elif mode=='duplicate': payload['classpath'].append(str(self.tests))
                elif mode=='newer':
                    file=self.tests/'dev/persefonia/app/webadmin/preview'/preview.CLASSES[0]
                    file.write_bytes(bytes.fromhex('cafebabe00000046'))
                with self.assertRaises(ValueError): preview.validate_runtime(payload,self.root,'a'*64,witness,True)
                if mode=='newer': file.write_bytes(bytes.fromhex('cafebabe00000041'))


    def test_only_explicit_fixture_classes_enter_the_preview_not_other_test_configs(self):
        import zipfile
        (self.tests/'OtherTestConfiguration.class').write_bytes(b'not-applicable')
        resources=self.root/'app/build/resources/test';resources.mkdir(parents=True)
        self.payload['classpath'].append(str(resources))
        self.validate()
        classpath=preview.isolate_fixture(self.payload,self.root).split(os.pathsep)
        self.assertNotIn(str(self.tests),classpath)
        self.assertNotIn(str(resources),classpath)
        with zipfile.ZipFile(classpath[-1]) as jar:
            self.assertEqual(set(jar.namelist()),{'dev/persefonia/app/webadmin/preview/'+name for name in preview.CLASSES})
