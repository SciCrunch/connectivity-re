# Connectivity-RE

Code/corpus repository for the Autonomic nervous system (ANS) connectivity extraction. For more details, see 
"Detecting Anatomical and Functional Connectivity Relations in Biomedical Literature via Language Representation Models" to appear at SDP2021 @@ NAACL-HLT 2021.

```bash
export CONNECTIVITY_RE_HOME=/full/path/to/connectivity_re/repository
```

## Download Bio-ELECTRA models

* The pre-trained Bio-ELECTRA mid and base sized models are available at Zenodo. [![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.4699034.svg)](https://doi.org/10.5281/zenodo.4699034)
* The pre-trained Bio-ELECTRA and Bio-ELECTRA++ small ELECTRA models are available at Zenodo. [![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.3971235.svg)](https://doi.org/10.5281/zenodo.3971235)

### Building Java code for data preparation
* You need Java 1.8+ and Gradle build system for the Java portion of the code.

```bash
cd $CONNECTIVITY_RE_HOME
gradle clean install
```

### ANS Connectivity Corpus

* The initial annotated corpus is in `$CONNECTIVITY_RE_HOME/data/sparc/base/sparc_connectivity_nerve_ganglia_sampled_idx_joe_full.xml`.
* The randomly sampled annotated corpus of 250 sentences for active learning control set is in `$CONNECTIVITY_RE_HOME/data/sparc/base/active_learning/sparc_connectivity_nerve_ganglia_random_set_annotated_idx.xml`.
* The annotated corpus for each of the ten iterations used for active learning tests are in `$CONNECTIVITY_RE_HOME/data/sparc/base/active_learning/iterations/sparc_base_al_iter_<iter-no>_curated_idx.xml`.

#### Viewing/Editing ANS Connectivity Corpus

```bash
cd $CONNECTIVITY_RE_HOME
java -jar relation-annotator-1.0.18.jar
```

### GPU requirements
You need Tensorflow 1.15 and CUDA 10.0 for GPU.


### Setup virtual environment

Ensure you have virtual environment support (e.g. for Ubuntu)
```
sudo apt-get install python3-venv
```

```
python3 -m venv --system-site-packages $CONNECTIVITY_RE_HOME/venv
```

```bash
source $CONNECTIVITY_RE_HOME/venv/bin/activate

pip install --upgrade pip
pip install tensorflow-gpu==1.15
pip install sklearn
pip install hyperopt

```

* The code for the ANS connectivity extraction classifiers are in `$CONNECTIVITY_RE_HOME/scripts/bio_electra`.
* The training/testing data for the binary and ternary classifiers are in `$CONNECTIVITY_RE_HOME/scripts/bio_electra/data/finetuning_data/sparc` and `$CONNECTIVITY_RE_HOME/scripts/bio_electra/data/finetuning_data/sparc-multi`, respectively.


For any questions, please contact iozyurt@ucsd.edu.
