# Connectivity-RE

```bash
export CONNECTIVITY_RE_HOME=/full/path/to/connectivity_re/repository
```

### Building Java code for data preparation
* You need Java 1.8+ and Gradle build system for the Java portion of the code.

```bash
cd $CONNECTIVITY_RE_HOME
gradle clean install
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


## Download Bio-ELECTRA models

* The pre-trained Bio-ELECTRA mid and base sized models are available at Zenodo. [![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.4699034.svg)](https://doi.org/10.5281/zenodo.4699034)
* The pre-trained Bio-ELECTRA and Bio-ELECTRA++ small ELECTRA models are available at Zenodo. [![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.3971235.svg)](https://doi.org/10.5281/zenodo.3971235)

